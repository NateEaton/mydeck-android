# MyDeck

[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Latest Release](https://img.shields.io/github/v/release/NateEaton/mydeck-android)](https://github.com/NateEaton/mydeck-android/releases/latest)

MyDeck is an independently maintained Android client for [Readeck](https://readeck.org/en/), a self-hosted read-it-later service. It provides a Pocket-style interface for managing and reading your saved articles, videos, and photos.

**Requirements:** A Readeck account and self-hosted Readeck server instance.

## Download

- **GitHub Releases:** [Latest release](https://github.com/NateEaton/mydeck-android/releases/latest)

## Key Features

- Pocket-style navigation with My List, Archive, and Favorites views
- Three bookmark list layouts: Grid, Compact, and Mosaic
- Full label management: add, rename, delete, and filter by labels
- Dual reading modes: Readeck-extracted Article view and embedded Original webview
- Article, photo, and video bookmark support with embedded media
- Reading progress tracking with per-card visual indicators
- Reading typography customization (font family, size, line spacing, content width)
- In-article text search with highlighted match navigation
- Share from any app — lightweight bottom sheet with auto-submit timer
- Adaptive layouts for tablet and landscape orientation
- OAuth Device Code Grant authentication
- Configurable content sync with Wi-Fi and battery-saver constraints

## Building

**Prerequisites:** Android Studio (latest stable) or Android SDK command-line tools, JDK 17+.

```bash
git clone https://github.com/NateEaton/mydeck-android.git
cd mydeck-android
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/githubSnapshot/debug/`. Release builds require signing configuration — refer to the GitHub Actions workflow in `.github/workflows/release.yml`.

## Contributing

Contributions are welcome. Please open an issue before starting significant work to align on approach. See [docs/WORKFLOW.md](docs/WORKFLOW.md) for the development and release workflow.

When adding new string resources, English placeholder strings must be added to all language files — see [CLAUDE.md](CLAUDE.md) for details.

## Acknowledgements

MyDeck is derived from [ReadeckApp](https://github.com/jensomato/ReadeckApp) by jensomato, licensed under the GNU General Public License v3.0. For a summary of changes from the original, see [docs/FORK_DIFFERENCES.md](docs/FORK_DIFFERENCES.md).

## License

[GNU General Public License v3.0](LICENSE)
