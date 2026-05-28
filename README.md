<div align="center">

# OnePlusPlusLauncher

**OxygenOS 16 / System Launcher 16.6.5 adaptation.**  
Original project: https://github.com/wizpizz/OnePlusPlusLauncher  
This fork was adapted for OOS16 by Zhangzong with assistance from OpenClaw.

## LSPosed Module for OnePlus System Launcher

</div>

![GitHub Release](https://img.shields.io/github/v/release/zhangbaoshengrio/OnePlusPlusLauncher-OOS16?style=for-the-badge)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/zhangbaoshengrio/OnePlusPlusLauncher-OOS16/debug_build.yml?style=for-the-badge&label=DEBUG%20BUILD)
![GitHub License](https://img.shields.io/github/license/zhangbaoshengrio/OnePlusPlusLauncher-OOS16?style=for-the-badge)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/zhangbaoshengrio/OnePlusPlusLauncher-OOS16/total?style=for-the-badge)
![GitHub Repo stars](https://img.shields.io/github/stars/zhangbaoshengrio/OnePlusPlusLauncher-OOS16?style=for-the-badge)

OnePlusPlusLauncher is an Xposed/LSPosed module for the System Launcher on OxygenOS 16 that hooks into the application using the [YukiHookAPI](https://github.com/HighCapable/YuKiHookAPI) framework. It modifies app drawer search functions: automating keyboard display, enabling instant app launch from search, redirecting search actions to the app drawer, and providing optional fuzzy search.

**Tested on System Launcher 16.6.5 (OnePlus 13, OxygenOS 16).**

**Please star the repository, if you enjoy using the module! It goes a long way ⭐**

## 📦 Installation

**Before downloading, please check the release notes of the version you are downloading to see if it is compatible with your launcher version.**

1. Make sure your device is rooted and you have LSPosed installed.
2. Download the latest release APK (or any other older release) from the [releases page](https://github.com/zhangbaoshengrio/OnePlusPlusLauncher-OOS16/releases)
3. Install the APK on your device.
4. Enable the module in the LSPosed manager and make sure System Launcher is enabled in the scope settings.
5. Restart System Launcher.

(Restarting the launcher may be necessary for changes to take effect after toggling features.)

## ⚡ Features

* ⌨️ **Automatic Keyboard / Searchbar Focus:** Automatically displays the keyboard when the app drawer is opened and search is focused. Can be toggled separately for opening app drawer by swiping up or redirecting from the Global Search Button.
* ↩️ **App Launch on Enter:** Launches the first search result directly when the "Enter" key or search action button on the keyboard is pressed.
* 🔎 **Global Search Button Redirect:** Intercepts the search button in the homescreen that would normally open the dedicated global search app, redirecting to the main app drawer instead.
* 📱 **Swipe Down Search Redirect:** Intercepts swipe down search gestures and redirects them to the app drawer instead of the default search interface. Includes optional auto focus for seamless search experience.
* 🍑 **Fuzzy Search:** Replaces the default search logic with a ranked fuzzy search algorithm for more flexible matching.
* ⚙️ **Configuration UI:** Allows toggling features individually, including auto focus options for different interaction methods.

## 🔮 To-Do

* Rewrite the module UI using Jetpack Compose instead of the current Android Views/XML implementation.
* A decent app icon 
* And many other refactorings and improvements...

## 🔧 Troubleshooting / Known Issues

*   **Compatibility / Launcher Updates:** Launcher updates may break hooks. Class names, field names, or method signatures might change, requiring updates to the module.
