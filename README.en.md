<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="180" alt="SearchUp icon" />

# SearchUp

### An LSPosed module that lets you swipe up, search immediately, and press Enter to launch

[한국어](README.md) | **English** | [日本語](README.ja.md)

</div>

SearchUp is an LSPosed module that turns the OnePlus OxygenOS 16 System Launcher app drawer into a faster search surface.

Swipe up from the home screen and the search field gets focus with the keyboard open. It also adds Korean initial-consonant search and Enter-to-launch, so you spend less time opening the drawer and tapping the search field again.

The original project is [OnePlusPlusLauncher](https://github.com/wizpizz/OnePlusPlusLauncher). This fork adds OxygenOS 16 support and the SearchUp flow.

## Features

- Automatically focuses the search field and shows the keyboard when entering the app drawer by swiping up
- Improves Korean/English search, including Hangul initial-consonant matching
- Launches the first search result with the keyboard Enter key
- Redirects the search button, swipe-down search, and left-swipe Discover entry into app drawer search
- Cleans up keyboard and search state when pressing Back from the search screen
- Restarts System Launcher from inside the app after setting changes
- Provides simple Korean/English/Chinese switching in the settings screen

## Compatibility

- Verified device base: OnePlus 13 / OxygenOS 16
- Verified launcher base: System Launcher `16.6.5`
- LSPosed scope: `com.android.launcher`
- App package: `com.wizpizz.onepluspluslauncher`
- Module version: `1.0.0-oos16.0.7.201`
- Minimum Android SDK: `27`
- Target Android SDK: `35`
- Minimum Xposed version: `93`

**Note:** This needs root and LSPosed. If a launcher update changes internal classes, some or all hooks may stop working. In that case, falling back to the original launcher behavior is expected until a new APK is shipped.

## Install

1. Download the latest APK from [Releases](https://github.com/devuterian/OnePlusPlusLauncher-OOS16/releases).
2. Install the APK.
3. Enable the SearchUp module in LSPosed.
4. Add System Launcher, `com.android.launcher`, to the module scope.
5. Tap `Apply changes: restart launcher` in SearchUp, or reboot the device.

If nothing works after installation, check LSPosed module activation and the scope first. Without both, opening the app does not change launcher behavior.

## Usage

The default flow is simple.

1. Swipe up from the home screen.
2. Type the app name when the keyboard appears.
3. Press Enter if the app you want is the first result.

The `SearchUp flow` section in settings is usually what you want enabled. Touch `Keyboard behavior (advanced)` and `Redirects (advanced)` only if you also want to change the search button, swipe-down search, or Discover redirect behavior.

## Build

This project uses Gradle Kotlin DSL. JDK 17 is the local baseline.

```sh
./gradlew test assembleRelease
```

Build outputs are written to the normal Gradle APK paths.

- release APK: `app/build/outputs/apk/release/app-release.apk`
- debug APK: `app/build/outputs/apk/debug/app-debug.apk`

Release signing is read from environment variables or a local `.env` file. Do not commit that file.

```text
SIGNING_KEY_STORE_PATH=
SIGNING_KEY_ALIAS=
SIGNING_KEY_STORE_PASSWORD=
SIGNING_KEY_PASSWORD=
```

If signing values are missing, release signing is skipped. Fill them in before building an APK you intend to distribute.

## Troubleshooting

- If the module status says `Not active yet`, enable SearchUp in LSPosed.
- If a setting does not apply immediately, tap the launcher restart button in the SearchUp app.
- If search hooks break after a launcher update, compare your System Launcher version with the verified version and release notes.
- If things get messy, disable the module in LSPosed and restart System Launcher to return to the original launcher behavior.

## License

This repository is licensed under `AGPL-3.0`. See [LICENSE](LICENSE) for details.

## Credits

- Original module: [wizpizz/OnePlusPlusLauncher](https://github.com/wizpizz/OnePlusPlusLauncher)
- OxygenOS 16 adaptation: [zhangbaoshengrio/OnePlusPlusLauncher-OOS16](https://github.com/zhangbaoshengrio/OnePlusPlusLauncher-OOS16)
- SearchUp maintenance fork: [devuterian/OnePlusPlusLauncher-OOS16](https://github.com/devuterian/OnePlusPlusLauncher-OOS16)
- Hook framework: [YukiHookAPI](https://github.com/HighCapable/YuKiHookAPI)
