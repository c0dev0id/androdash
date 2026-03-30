<p align="center">
  <img src="docs/banner.svg" alt="Androdash" width="100%"/>
</p>

<p align="center">
  <b>A fast, keyboard-driven Android launcher with letter-based navigation.</b>
</p>

---

## Screenshots

<p align="center">
  <img src="docs/screenshots/main_grid.png" alt="Main app grid" width="45%"/>
  &nbsp;
  <img src="docs/screenshots/letter_filter.png" alt="Letter filtering" width="45%"/>
</p>
<p align="center">
  <img src="docs/screenshots/app_menu.png" alt="App context menu" width="45%"/>
  &nbsp;
  <img src="docs/screenshots/sorted_grid.png" alt="Sorted app grid" width="45%"/>
</p>

## Features

- **Letter-based navigation** -- Tap letters to progressively filter apps. Type "A", then "N", then "D" to quickly find "Androdash". Available letters update dynamically based on your current selection.

- **Keyboard support** -- Plug in a keyboard and just start typing. Press Enter to launch the first match, Backspace to clear.

- **Configurable letterbar** -- Position the letter bar on any edge: top, bottom, left, or right. The grid layout adapts automatically.

- **Usage-based sorting** -- The launcher learns which letters you pick most often and reorders them for faster access.

- **App history** -- A row of recently launched apps appears at the top for quick re-access.

- **Long-press menu** -- Shows app shortcuts (when set as default launcher), plus options to hide, uninstall, or open app info.

- **Hide apps** -- Remove apps from the grid without uninstalling. Toggle visibility from the config panel or long-press menu.

- **Dark theme** -- A dark interface with orange accents, designed for low-distraction use.

- **Auto-update** -- Check for and install updates directly from the config panel via GitHub nightly releases.

## Requirements

- Android 14+ (API 34)

## Installation

Download the latest APK from the [Releases](../../releases) page and install it on your device. When prompted to choose a home app, select **Androdash**.

To get full app shortcut access in the long-press menu, set Androdash as your default launcher.

## Configuration

Tap the gear icon (&#9881;) in the letter bar to reveal five toggles:

| Toggle | Description |
|--------|-------------|
| **Show all Apps** | Reveal hidden apps (shown at reduced opacity) |
| **Lettersort** | Switch between alphabetical and usage-based letter ordering |
| **Letterbar Position** | Cycle through top / bottom / left / right placement |
| **App History** | Show or hide the recent apps row |
| **Version** | View current build and check for updates |

## Building

```
./gradlew assembleDebug
```

## License

See repository for license details.
