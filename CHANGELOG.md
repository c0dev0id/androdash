# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Physical/Bluetooth keyboard support: typing a letter selects it on the letter bar, Backspace removes the last selected letter, Escape clears all selected letters, arrow keys move focus freely across the letter bar and app grid, and Enter activates the focused item.

### Fixed
- Apps no longer drop out of their folder when updated. The package-removed receiver now skips folder cleanup when the broadcast carries `EXTRA_REPLACING`, so updates (which fire REMOVED+ADDED) preserve folder membership.

### Removed
- DMD wired remote control support (broadcast-based D-pad navigation).
