<div align="center">

# SearchUp

**Swipe up. Search immediately.**

LSPosed module for the OnePlus / OxygenOS 16 System Launcher.

![GitHub Release](https://img.shields.io/github/v/release/devuterian/OnePlusPlusLauncher-OOS16?style=for-the-badge)
![GitHub License](https://img.shields.io/github/license/devuterian/OnePlusPlusLauncher-OOS16?style=for-the-badge)
![GitHub Downloads](https://img.shields.io/github/downloads/devuterian/OnePlusPlusLauncher-OOS16/total?style=for-the-badge)

</div>

SearchUp makes the OxygenOS 16 app drawer behave like a fast launcher search surface: swipe up from the home screen, land directly in search, type in Korean or English, and launch the result you wanted.

This project is based on [OnePlusPlusLauncher](https://github.com/wizpizz/OnePlusPlusLauncher), adapted for OxygenOS 16 and continued as SearchUp for faster app drawer search.

## Compatibility

- Tested target: OnePlus System Launcher `16.6.5`
- Tested device base: OnePlus 13 / OxygenOS 16
- Requires root and LSPosed
- LSPosed scope: enable System Launcher

Launcher updates can break hooks. If SearchUp stops working after a System Launcher update, check the release notes before updating.

## Features

- **Swipe-up search focus:** opens app drawer search and shows the keyboard automatically.
- **Gesture-back cleanup:** when search is open, one back gesture closes the keyboard and search surface together.
- **Korean fuzzy search:** supports Hangul syllable search and initial-consonant search such as `ㅋㅌ` for Korean app names.
- **App shortcut search:** optionally includes launcher shortcuts from long-press app menus in search results.
- **Enter to launch:** launches the first search result from the keyboard action key.
- **Search redirects:** redirects global search, swipe-down search, and left-swipe Discover gestures into app drawer search.
- **Launcher restart:** includes an in-app action and app shortcut to restart System Launcher after changing options.
- **Localized settings:** Korean Android locales show natural Korean UI text.

## Installation

1. Install the latest APK from [Releases](https://github.com/devuterian/OnePlusPlusLauncher-OOS16/releases).
2. Enable SearchUp in LSPosed.
3. Add System Launcher to the module scope.
4. Restart System Launcher from the SearchUp app or from LSPosed.

## Recommended Settings

For the SearchUp-style flow, enable:

- Auto Focus on Swipe Up
- Use Fuzzy Search Algorithm
- Search App Shortcuts, if you want app long-press shortcuts to appear in search

## Build

```bash
./gradlew assembleRelease
```

Release signing reads these values from the environment or a local `.env` file:

```text
SIGNING_KEY_STORE_PATH=
SIGNING_KEY_ALIAS=
SIGNING_KEY_STORE_PASSWORD=
SIGNING_KEY_PASSWORD=
```

## Credits

- Original module: [wizpizz/OnePlusPlusLauncher](https://github.com/wizpizz/OnePlusPlusLauncher)
- OxygenOS 16 adaptation: [zhangbaoshengrio/OnePlusPlusLauncher-OOS16](https://github.com/zhangbaoshengrio/OnePlusPlusLauncher-OOS16)
- Maintained release fork: [devuterian/OnePlusPlusLauncher-OOS16](https://github.com/devuterian/OnePlusPlusLauncher-OOS16)
- Built with [YukiHookAPI](https://github.com/HighCapable/YuKiHookAPI)
