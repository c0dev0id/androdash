# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Physical/Bluetooth keyboard support: typing a letter selects it on the letter bar, Backspace removes the last selected letter, Escape clears all selected letters, arrow keys move focus freely across the letter bar and app grid, and Enter activates the focused item.

### Changed
- Nightly builds now carry a distinct, increasing `versionCode` (the CI run number) and a `versionName` of the form `nightly-<commit>`. Previously every build was `1` / `1.0`.
- All dialogs share one visual style. The Backup and Add-to-Folder dialogs used smaller button text, a bold title and pixel-based padding that did not match the other dialogs; every dialog now has the same title size, button style, padding and item spacing. Dialog width is 90% of the display on narrow screens and a fixed 480dp on landscape phones and tablets, instead of always 90%.
- App long-press dialog: replaced the cycling "Hide / Hide from History / Show" button with two independent toggles ("Hide from app list", "Hide from history") under a "Hide app" label. Toggles persist immediately and the dialog stays open, so any combination is reachable in one click without re-navigating to the app.

### Fixed
- Apps no longer drop out of their folder when updated. The package-removed receiver now skips folder cleanup when the broadcast carries `EXTRA_REPLACING`, so updates (which fire REMOVED+ADDED) preserve folder membership.
- Backup/restore now preserves the per-app "hide from history" flag. The export and import paths previously round-tripped only `hidden_packages` and silently dropped `history_excluded_packages`. Backups taken before this change can still be imported — the missing key is treated as empty.

### Removed
- DMD remote broadcast listener. The DMD companion app now signature-protects its keypress broadcast, so the app can no longer receive it. The remote's D-pad, Enter and Escape are handled as regular key events instead; held buttons no longer auto-repeat.
- The unused `KILL_BACKGROUND_PROCESSES` permission. It was declared in the manifest but never used by any code path.
- The `QUERY_ALL_PACKAGES` permission. The app only enumerates launchable apps, which the manifest `<queries>` block already covers, so the broad permission was never required.
