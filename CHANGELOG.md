# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Physical/Bluetooth keyboard support: typing a letter now selects it on the letter bar (if available), Backspace removes the last selected letter, and pressing Enter launches the first app in the current result list.
- Apps and bookmarks can now be excluded from history without hiding them from the app grid.
- andRemote2 joystick support: the 360° joystick moves focus freely between the letter bar and the app grid. Button 1 activates the focused element; Button 2 clears all selected letters and returns focus to the first letter button.
- Full d-pad / arrow-key navigation: directional input moves Android native focus across both the letter bar and the app grid. Enter activates the focused element, Escape clears all letter selections.

### Changed
- HiddenAppsStore now writes both hidden and history-excluded sets in a single atomic SharedPreferences transaction (was two separate disk writes per mutation).
- Remote input handling replaced: the custom letter-bar focus index system is removed in favour of Android's native view focus traversal, enabling fluid navigation between all interactive UI elements.
- Joystick navigation now parses composite joy payloads (e.g. `U5L4`) by picking the axis with the largest magnitude; navigation only fires at full deflection (magnitude 5), so lower magnitudes and diagonals without a dominant full-deflection axis are ignored to prevent accidental input.
- D-pad key handling removed from `dispatchKeyEvent`; joystick navigation is broadcast-only.
