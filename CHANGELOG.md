# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Physical/Bluetooth keyboard support: typing a letter now selects it on the letter bar (if available), Backspace removes the last selected letter, and pressing Enter launches the first app in the current result list.
- Apps and bookmarks can now be excluded from history without hiding them from the app grid.
- andRemote2 joystick support: the 360° joystick moves focus freely between the letter bar and the app grid. Button 1 activates the focused element; Button 2 clears all selected letters and returns focus to the first letter button.
- Full d-pad / arrow-key navigation: directional input moves Android native focus across both the letter bar and the app grid. Enter activates the focused element, Escape clears all letter selections.

### Fixed
- Remote navigation and ENTER/ESCAPE focus loss: remote input via `BroadcastReceiver` bypasses `ViewRootImpl` so Android's touch mode is never exited automatically, causing `requestFocus()` to silently fail on `Button` (not touch-focusable by default). All remote-triggered focus calls now use `requestFocusFromTouch()`, which exits touch mode before granting focus. Directional navigation also uses `FocusFinder` scoped to the letter bar's scroll container so the container itself is never selected as the focus target.
- Letter bar focus lost when touching a touchscreen remote overlay: letter buttons are now `focusableInTouchMode`, so they retain focus when the device enters touch mode (any touchscreen contact). Previously, each overlay interaction cleared focus from the button before the broadcast arrived.
- Remote focus disappearing when pressing ENTER on a letter button: a deferred focus fallback now restores focus to the first available button after any action that removes the focused view from the tree.
- Joystick input causing RecyclerView to scroll and lose focus: `onGenericMotionEvent` now consumes `SOURCE_JOYSTICK` events at the Activity level, so RecyclerView's built-in joystick-scroll handler can no longer recycle the focused item off-screen.
- Joystick `joyHeld` latch never resetting when a remote releases only one axis at a time: the neutral sentinel check now recognises `"Y0"` and `"X0"` in addition to `"Y0X0"`, matching the actual hardware behaviour.

### Changed
- HiddenAppsStore now writes both hidden and history-excluded sets in a single atomic SharedPreferences transaction (was two separate disk writes per mutation).
- Remote input handling replaced: the custom letter-bar focus index system is removed in favour of Android's native view focus traversal, enabling fluid navigation between all interactive UI elements.
- Joystick navigation now parses composite joy payloads (e.g. `U5L4`) by picking the axis with the largest magnitude and treats the joystick as a digital key: a magnitude-5 payload is a key-down that fires exactly one navigation step; the neutral sentinel `Y0X0` is a key-up that re-arms the latch. Intermediate payloads (sub-5 magnitudes or drift while held) are ignored, so one physical push = one focus move.
- D-pad key handling removed from `dispatchKeyEvent`; joystick navigation is broadcast-only.
