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

**Joy string format**: andRemote2 sends strings like `"L5"`, `"U5L4"`, `"U3R4"` — one or more `<axis><magnitude>` pairs concatenated, where axis ∈ {U,D,L,R} and magnitude is a digit. We parse every pair and pick the axis with the largest magnitude (first one wins on ties); navigation fires only when the dominant magnitude is 5. This correctly handles diagonal payloads where one axis is fully deflected and the other is crosstalk (e.g. `"U5L4"` navigates UP), while still ignoring sub-max input (`"U4L4"` is dropped) to avoid accidental navigation from imprecise input.

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
