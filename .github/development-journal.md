# Development Journal

## Software Stack

- **Language**: Java (app), Kotlin (andRemote2 companion app)
- **Platform**: Android (minSdk TBD, targets modern Android)
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

**Joy string format** (andRemote2 SPECIFICATION.md §"Signal 1"): strings like `"L5"`, `"U5L4"`, `"U3R4"` — one or more `<axis><magnitude>` pairs where axis ∈ {U,D,L,R} and magnitude ∈ {2..5}. `"Y0X0"` is the neutral sentinel emitted once on release. The string is only broadcast when it changes, so natural finger drift at full deflection produces rapid bursts of near-identical payloads (e.g. `U5` → `U5L2` → `U5`).

**Keydown/keyup model**: we treat the joystick as a digital key via a `joyHeld` boolean latch. A payload whose dominant axis has magnitude 5 is a key-down: navigate once in that direction, set `joyHeld = true`. `"Y0X0"` is a key-up: clear `joyHeld`. Anything else (sub-5 magnitude payloads, drift payloads received while `joyHeld`) is ignored. Net effect: one physical push = one focus move, even under drift.

### Remote Focus Disappearing Fixed (2026-04-15)

Two independent root causes, both fixed in `MainActivity`:

**Root Cause A — RecyclerView joystick scroll**: Android TV recognises the physical remote hardware as a `SOURCE_JOYSTICK` device and dispatches `GenericMotionEvent` to `RecyclerView`, which has built-in joystick-scroll handling (`scrollByInternal`). This recycled the focused item off-screen independently of the broadcast receiver. Fix: override `onGenericMotionEvent` to consume all `SOURCE_JOYSTICK` events at Activity level. Navigation is broadcast-only; native joystick motion is noise.

**Root Cause B — ENTER removes focused view**: Pressing ENTER on a letter button calls `performClick()` → `onAvailableLetterClick()` → `updateButtons()`, which removes the focused view (tag `a:X`) and adds a selected view (tag `s:0:X`) in its place. Android clears window focus when the focused view is removed. Fix: `rootLayout.post(() -> { if (getCurrentFocus() == null) letterBar.focusFirstButton(); })` at the end of `onReceive()`. The `post()` defers the check until after `updateButtons()` runs on the same main-thread `MessageQueue`.

**Lesson**: when view mutations and focus checks need ordering guarantees on the main thread, `View.post()` is the correct tool — it enqueues after all pending layout/draw work without introducing inter-thread complexity.

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
