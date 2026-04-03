# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Physical/Bluetooth keyboard support: typing a letter now selects it on the letter bar (if available), Backspace removes the last selected letter, and pressing Enter launches the first app in the current result list.
- Apps and bookmarks can now be excluded from history without hiding them from the app grid.

### Changed
- HiddenAppsStore now writes both hidden and history-excluded sets in a single atomic SharedPreferences transaction (was two separate disk writes per mutation).
