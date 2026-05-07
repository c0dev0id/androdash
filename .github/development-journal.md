# Development Journal

## Software Stack

- **Language**: Java (app), Kotlin (andRemote2 companion app)
- **Platform**: Android (minSdk 34, targets modern Android)
- **UI**: Programmatic layouts (no XML for main UI), RecyclerView + GridLayoutManager for app grid
- **Build**: AGP via CI only — local Gradle builds are blocked by proxy/network restrictions
- **Input**: Broadcast intents (`com.thorkracing.wireddevices.keypress`) for remote/joystick; `dispatchKeyEvent` for physical keyboards

## Key Decisions

### Native Android Focus Traversal (2026-04-14)
Replaced the custom `focusedAvailableIndex` integer tracking in `LetterBar` with Android's native `View.focusSearch()` + `requestFocus()` system.

**Why**: The custom system only covered the letter bar and had no concept of the app grid. The new approach lets focus move freely between letter buttons and RecyclerView items with zero extra navigation code — `GridLayoutManager` and the spatial view hierarchy handle all traversal automatically.

**How it works**: `Button` objects in `LetterBar` are focusable by default; `LinearLayout` items in the RecyclerView are made focusable via `setFocusable(true)` in `onCreateViewHolder`. `OnFocusChangeListener` on each view swaps the background drawable (`bg_button_focused` / `bg_app_card_focused`) on focus gain/loss. The `navigateFocus(direction)` helper in `MainActivity` calls `getCurrentFocus().focusSearch(direction).requestFocus()`.

**Input paths**:
- Remote (`com.thorkracing.wireddevices.keypress` broadcast): joystick `joy` string parsed as a sequence of `<axis><magnitude>` pairs; the axis with the largest magnitude wins and navigation fires only when that magnitude is 5; `key_press` keycodes for Enter/Escape route to `performClick()` / `clearAllLetters()`.
- Physical keyboard (`dispatchKeyEvent`): Enter → `performClick()`, Escape → `clearAllLetters()` + `focusFirstButton()`, Backspace → `removeLastLetter()`, letter keys → `selectLetter()`. D-pad keys are NOT handled here — broadcast-only.

**Joy string format** (andRemote2 SPECIFICATION.md §"Signal 1"): strings like `"L5"`, `"U5L4"`, `"U3R4"` — one or more `<axis><magnitude>` pairs where axis ∈ {U,D,L,R} and magnitude ∈ {2..5}. The neutral sentinels emitted on release are `"Y0X0"` (both axes simultaneously), `"Y0"` (Y-axis only), or `"X0"` (X-axis only). The string is only broadcast when it changes, so natural finger drift at full deflection produces rapid bursts of near-identical payloads (e.g. `U5` → `U5L2` → `U5`).

**Keydown/keyup model**: we treat the joystick as a digital key via a `joyHeld` boolean latch. A payload whose dominant axis has magnitude 5 is a key-down: navigate once in that direction, set `joyHeld = true`. Any of `"Y0X0"`, `"Y0"`, or `"X0"` is a key-up: clear `joyHeld`. Anything else (sub-5 magnitude payloads, drift payloads received while `joyHeld`) is ignored. Net effect: one physical push = one focus move, even under drift.

### Remote Focus Handling Architecture (2026-04-18)

Remote input via `BroadcastReceiver` bypasses `ViewRootImpl` entirely, which means Android's touch mode is never exited automatically. This is the fundamental difference from physical keyboard input and the root cause of all remote focus issues. Three mechanisms now cooperate to keep focus working:

1. **`requestFocusFromTouch()` everywhere**: All remote-triggered focus calls use `requestFocusFromTouch()` instead of `requestFocus()`. The former calls `viewRoot.ensureTouchMode(false)` internally, exiting touch mode before granting focus. Without this, `requestFocus()` silently fails on `Button` (not focusable in touch mode by default).

2. **Scoped `FocusFinder`**: `navigateFocus()` uses `FocusFinder.getInstance().findNextFocus(letterScrollView, current, direction)` when the focused view is inside the letter bar. This prevents `HorizontalScrollView`/`ScrollView` (focusable containers with no visible indicator) from winning focus. Falls back to window-wide `focusSearch()` for cross-boundary navigation (bar ↔ app grid). An `isDescendantOf()` guard prevents an `IllegalArgumentException` crash when the focused view is not a descendant of the search root. Note: `View.isDescendantOf()` exists in AOSP but is `@hide` — the custom helper is necessary.

3. **`OnGlobalFocusChangeListener`**: Detects when focus is cleared without a navigation target (touch-mode entry from overlay touch). If `newFocus == null` and `oldFocus` is still attached to the window, focus was cleared by touch mode — not by a layout change. A deferred `requestFocusFromTouch()` restores it. The ENTER case (button removed from tree) is excluded because `isAttachedToWindow()` returns false after `removeViewAt()`.

Additionally, ENTER key handling in the broadcast receiver uses a scoped `post()` fallback to restore focus after `updateButtons()` removes the focused view from the tree.

**Earlier fixes still in effect**: `onGenericMotionEvent` consumes `SOURCE_JOYSTICK` events at Activity level (prevents RecyclerView joystick-scroll from recycling focused items). Joy neutral sentinel check accepts `"Y0"` and `"X0"` in addition to `"Y0X0"` (matches hardware that releases axes independently).

### Per-app hide UI: two independent toggles (2026-05-07)
The app long-press dialog used a single tri-state TextView (`btnHideShow`) that cycled show → hide → hide-from-history → show, dismissing the dialog on every press. Reaching the third state required re-opening the dialog and a config-mode detour because the just-hidden app vanished from the grid.

`HiddenAppsStore` already kept two independent sets (`hidden_packages`, `history_excluded_packages`); the tri-state was an artificial UX-layer mutual exclusion. The store now exposes `setHidden(pkg, bool)` and `setExcludedFromHistory(pkg, bool)` and lets both flags be true simultaneously. The dialog has two TextView toggles ("Hide from app list", "Hide from history") that swap between `bg_button` and `bg_button_selected` on click — the selected drawable is already a state-list selector, so focus styling for remote-control nav works without extra drawables. Toggles persist immediately and fire the existing `onAppHiddenChanged` listener so the grid refreshes live behind the dialog. The dialog only dismisses on the explicit "Close" button.

`MainActivity.refreshDisplayedApps()` already AND-ed both flags in its history filter and gated the grid filter on `isHidden` only, so all four flag combinations were correctly handled with no filter changes. Existing user data carries forward unchanged because the legacy three-way state is a strict subset of the new state space.

### Package-update vs. uninstall must check `EXTRA_REPLACING` (2026-05-07)
App updates fire `ACTION_PACKAGE_REMOVED` followed by `ACTION_PACKAGE_ADDED`, both with `EXTRA_REPLACING=true`. Treating `REMOVED` as a real uninstall means destructive cleanup runs on every update. The package receiver now guards `folderStore.removePackageFromAllFolders(...)` behind a `!EXTRA_REPLACING` check; the apps-dirty flag is still set unconditionally so icon/label changes still trigger a reload. Any future per-package state (bookmarks, history, hidden flags) added to the same receiver must apply the same guard.

### Stale `focusedAvailableIndex` Reference Fixed (2026-04-14)
The `clearSelection()` method in `LetterBar` still contained `focusedAvailableIndex = -1` after the native focus traversal refactor removed the field. This caused a compile error in CI. When removing fields during a refactor, search for all write sites (reset/clear assignments), not just read sites — IDEs often miss stale writes.

### andRemote2 Joystick Support (2026-04-14)
The 360° joystick in andRemote2 sends `joy` extras via the same broadcast action. Previously ignored entirely. Now handled by `remoteListener` alongside `key_press` button events.

### HID Suppression Block Removed (2026-04-14)
The old `dispatchKeyEvent` contained a block that suppressed DPAD/Enter/Escape keycodes when source included `SOURCE_KEYBOARD` (to prevent DMD remote double-processing). This was removed because the remote communicates exclusively via broadcasts and does not generate HID keyboard events.

## Core Features

- **App launcher**: filterable grid of installed apps, bookmarks, and folders
- **Letter bar**: tappable/navigable character buttons that progressively filter the app list; supports beginning, anywhere, and fuzzy match modes
- **Folder support**: group apps into named folders with custom icons
- **History**: recently launched apps shown at top of grid (with per-app opt-out)
- **Remote control**: full navigation via andRemote2 joystick (360° mode) and button remotes using the `com.thorkracing.wireddevices.keypress` broadcast protocol
- **Keyboard navigation**: arrow keys, Enter, Escape, Backspace, and letter keys all work from a physical keyboard
- **Hidden apps**: apps can be hidden from the grid or excluded from history independently
- **Config mode**: gear button enters settings screen within the launcher
