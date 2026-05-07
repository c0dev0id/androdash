# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Physical/Bluetooth keyboard support: typing a letter selects it on the letter bar, Backspace removes the last selected letter, Escape clears all selected letters, arrow keys move focus freely across the letter bar and app grid, and Enter activates the focused item.

### Changed
- App long-press dialog: replaced the cycling "Hide / Hide from History / Show" button with two independent toggles ("Hide from app list", "Hide from history") under a "Hide app:" label. Toggles persist immediately and the dialog stays open, so any combination is reachable in one click without re-navigating to the app.

### Fixed
- Apps no longer drop out of their folder when updated. The package-removed receiver now skips folder cleanup when the broadcast carries `EXTRA_REPLACING`, so updates (which fire REMOVED+ADDED) preserve folder membership.
- Backup/restore now preserves the per-app "hide from history" flag. The export and import paths previously round-tripped only `hidden_packages` and silently dropped `history_excluded_packages`. Backups taken before this change can still be imported — the missing key is treated as empty.

### Removed
- DMD wired remote control support (broadcast-based D-pad navigation).
