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
- Remote focus disappearing when pressing ENTER on a letter button: a deferred focus fallback now restores focus to the first available button after any action that removes the focused view from the tree.
- Joystick input causing RecyclerView to scroll and lose focus: `onGenericMotionEvent` now consumes `SOURCE_JOYSTICK` events at the Activity level, so RecyclerView's built-in joystick-scroll handler can no longer recycle the focused item off-screen.

### Changed
- HiddenAppsStore now writes both hidden and history-excluded sets in a single atomic SharedPreferences transaction (was two separate disk writes per mutation).
- Remote input handling replaced: the custom letter-bar focus index system is removed in favour of Android's native view focus traversal, enabling fluid navigation between all interactive UI elements.
- Joystick navigation now parses composite joy payloads (e.g. `U5L4`) by picking the axis with the largest magnitude and treats the joystick as a digital key: a magnitude-5 payload is a key-down that fires exactly one navigation step; the neutral sentinel `Y0X0` is a key-up that re-arms the latch. Intermediate payloads (sub-5 magnitudes or drift while held) are ignored, so one physical push = one focus move.
- D-pad key handling removed from `dispatchKeyEvent`; joystick navigation is broadcast-only.
