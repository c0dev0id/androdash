# Development Journal

## Overview and Intent

Androdash is a launcher for devices where scrolling a grid is slow or impractical: phones and tablets used one-handed, and mounted devices driven by a hardware remote. Instead of scrolling, the user narrows the app list by selecting letters on a letter bar; each selection filters the grid and prunes the bar down to letters that still match. The same letter bar is reachable by touch, by physical keyboard, and by a HID remote, so one navigation model covers every input device.

## Software Stack

- **Language**: Java
- **Platform**: Android (minSdk 34, targets modern Android)
- **UI**: Programmatic layouts (no XML for main UI), RecyclerView + GridLayoutManager for app grid
- **Build**: AGP via CI only — local Gradle builds are blocked by proxy/network restrictions
- **Input**: `dispatchKeyEvent` in `MainActivity` for every key source: physical/Bluetooth keyboards and the DMD remote, which enumerates as a USB-HID keyboard

## Key Decisions

### Versioning and permissions driven by Play Protect review (2026-10-12)

The app was flagged by Google Play Protect. A comparison against motoLauncher (same signing key, same updater, not flagged) left three differences, all removed:

- `KILL_BACKGROUND_PROCESSES` was declared but unused.
- `QUERY_ALL_PACKAGES` was declared but unnecessary: all enumeration uses `queryIntentActivities` for MAIN/LAUNCHER, which the `<queries>` element already covers.
- Every nightly shipped as `versionCode 1` / `versionName "1.0"`, so one package name appeared as many unrelated binaries claiming the same version. CI now passes `-PappVersionCode=$GITHUB_RUN_NUMBER` and `-PappVersionName=nightly-<sha>`; `build.gradle` falls back to `1` / `dev-local` when the properties are absent.

The in-app updater still compares `BuildConfig.GIT_HASH` against the release asset name, not `versionCode`.

### Input and Focus Model (current as of 2026-10-12)

All navigation uses Android's native focus system. There is no custom focus index or direction handling in the app.

**Focusable views**:
- Letter bar buttons: `setFocusableInTouchMode(true)` in `LetterBar.createLetterButton()`, so they keep focus after a touch.
- App grid items: `item_app.xml` sets `focusable` and `focusableInTouchMode`. The `RecyclerView` itself is `setFocusable(false)` with `FOCUS_AFTER_DESCENDANTS` so D-pad traversal lands on items, not the container.
- Focus highlight is a `state_focused` item in the `bg_button`, `bg_button_selected`, `bg_app_card` and `bg_app_card_history` selector drawables (all point at `bg_button_focused`). No `OnFocusChangeListener` is needed.
- `AppGridAdapter.FOCUS_ON_TOUCH` is the shared touch listener for grid items and letter buttons: `ACTION_DOWN` requests focus and arms a long-press timer, `ACTION_UP` fires the click. This keeps touch and key input on the same focused view.

**Key handling** (`MainActivity.dispatchKeyEvent`, `ACTION_DOWN` only):
- Any event with `getRepeatCount() > 0` is consumed and ignored. A held key acts once.
- Backspace → `LetterBar.removeLastLetter()`
- Escape → `clearSelection()` if letters are selected, else `focusFirstAvailable()`
- Enter / numpad Enter → `performClick()` on `getCurrentFocus()`
- Letter keys → `LetterBar.selectLetter(char)`
- Everything else, including D-pad, falls through to `super.dispatchKeyEvent()` and the framework's `focusSearch()` traversal.
- Hardware back (`onBackPressed`) → `removeLastLetter()`

**Focus survival across rebuilds**: `LetterBar.updateButtons()` rebuilds the button row on every selection change, which would drop focus. Before rebuilding it records the focused button's tag; after rebuilding it re-requests focus on the button with that tag, falling back to the corresponding available-letter button when a selected letter was just removed.

### DMD Remote: Broadcast Protocol Replaced by HID Key Events (2026-10-12)

Until this date the DMD remote was driven by a `BroadcastReceiver` on `com.thorkracing.wireddevices.keypress` (`key_press` extras for buttons, `joy` strings for the joystick). The DMD companion app now signature-protects that broadcast, so third-party apps cannot receive it. The remote still enumerates as a USB-HID keyboard and emits D-pad for the joystick, Enter for button 1 and Escape for button 2.

The receiver, its `onResume`/`onPause` registration, the joystick string parser, and the 100 ms timestamp hack that dropped the duplicate KeyEvent each button press used to produce were all removed. The remote now takes the same `dispatchKeyEvent` path as a keyboard. Because input arrives through `ViewRootImpl`, touch mode exits on its own; the earlier broadcast-era workarounds (`requestFocusFromTouch()`, scoped `FocusFinder`, `OnGlobalFocusChangeListener`, `onGenericMotionEvent` joystick suppression) are gone and must not be reintroduced.

Lesson from the broadcast era: injecting input from outside the view system means fighting touch mode, focus restoration and duplicate events by hand. Prefer input that arrives as `KeyEvent`.

### Native Android Focus Traversal (2026-04-14)

Replaced custom index-based focus tracking in `LetterBar` with `View.focusSearch()` + `requestFocus()`. The custom system only covered the letter bar and had no concept of the app grid; native traversal moves freely between letter buttons and RecyclerView items with no navigation code. Note: `LetterBar` still carries the unused `focusedAvailableIndex` field and the `focusNext()` / `focusPrev()` / `selectFocused()` methods from the old system. Nothing calls them.

### Per-app hide UI: two independent toggles (2026-05-07)
The app long-press dialog used a single tri-state TextView (`btnHideShow`) that cycled show → hide → hide-from-history → show, dismissing the dialog on every press. Reaching the third state required re-opening the dialog and a config-mode detour because the just-hidden app vanished from the grid.

`HiddenAppsStore` already kept two independent sets (`hidden_packages`, `history_excluded_packages`); the tri-state was an artificial UX-layer mutual exclusion. The store now exposes `setHidden(pkg, bool)` and `setExcludedFromHistory(pkg, bool)` and lets both flags be true simultaneously. The dialog has two TextView toggles ("Hide from app list", "Hide from history") that swap between `bg_button` and `bg_button_selected` on click — the selected drawable is already a state-list selector, so focus styling for remote-control nav works without extra drawables. Toggles persist immediately and fire the existing `onAppHiddenChanged` listener so the grid refreshes live behind the dialog. The dialog only dismisses on the explicit "Close" button.

`MainActivity.refreshDisplayedApps()` already AND-ed both flags in its history filter and gated the grid filter on `isHidden` only, so all four flag combinations were correctly handled with no filter changes. Existing user data carries forward unchanged because the legacy three-way state is a strict subset of the new state space.

### Package-update vs. uninstall must check `EXTRA_REPLACING` (2026-05-07)
App updates fire `ACTION_PACKAGE_REMOVED` followed by `ACTION_PACKAGE_ADDED`, both with `EXTRA_REPLACING=true`. Treating `REMOVED` as a real uninstall means destructive cleanup runs on every update. The package receiver now guards `folderStore.removePackageFromAllFolders(...)` behind a `!EXTRA_REPLACING` check; the apps-dirty flag is still set unconditionally so icon/label changes still trigger a reload. Any future per-package state (bookmarks, history, hidden flags) added to the same receiver must apply the same guard.

## Core Features

- **App launcher**: filterable grid of installed apps, bookmarks, and folders
- **Letter bar**: tappable/navigable character buttons that progressively filter the app list; supports beginning, anywhere, and fuzzy match modes
- **Folder support**: group apps into named folders with custom icons
- **History**: recently launched apps shown at top of grid (with per-app opt-out)
- **Keyboard and remote navigation**: arrow keys / D-pad move focus, Enter activates, Escape clears, Backspace removes the last letter, letter keys select; the DMD remote and physical keyboards share this path
- **Hidden apps**: apps can be hidden from the grid or excluded from history independently
- **Config mode**: gear button enters settings screen within the launcher
