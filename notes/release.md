# 🚀 OnePlusPlusLauncher v1.3.4-oos16.6.5 Release

## 📝 Changelog

* Compatibility:
    * Target launcher version updated to **System Launcher 16.6.5** on OxygenOS 16.
    * Fixed swipe-down redirect auto focus path by wiring `auto_focus_swipe_down_redirect` to real focus logic.
    * Unified global-search redirect focus with shared `HookUtils.focusSearchInput` retry flow.
    * Added additional swipe-up state transition hook coverage (`onStateSetStart` overload + `onStateSetEnd`) for launcher API drift.
    * Added fallback search manager resolution for newer launcher internals.

## ✅ Validation target

* OnePlus 13 / OxygenOS 16 / System Launcher 16.6.5

---

# 🚀 OnePlusPlusLauncher v1.2.1 Release 

## 📝 Changelog

* Misc:
    * [[#4](https://github.com/wizpizz/OnePlusPlusLauncher/issues/4)] Fuzzy search now uses the lightweight [FuzzyWuzzy](https://github.com/seatgeek/fuzzywuzzy) library that utilizes the [Levenshtein Distance](https://en.wikipedia.org/wiki/Levenshtein_distance) algorithm to find the closest match. 
    It handles simple typos well without any noticeable performance degredation.
    The code has also been tweaked to sanitize spaces & quotation marks from the query, since users of non-English IMEs (e.g. Chinese Pinyin) often produce search queries with such characters. 
    The calculation method has also been tweaked to prefer matches where the query is a prefix of the target or if the query chars subsequently appear in the target. (Thanks to **[@deltazefiro](https://github.com/deltazefiro)**!)

Full Changelog: [`v1.2.0...v1.2.1`](https://github.com/wizpizz/OnePlusPlusLauncher/compare/v1.2.0...v1.2.1)

## ⬇️ Installation

1. Make sure your device is rooted and you have LSPosed installed.
2. Download the latest release APK from the assets section below or simply [click here](https://github.com/Xposed-Modules-Repo/com.wizpizz.onepluspluslauncher/releases/download/4-1.2.1/app-release.apk)
3. Install the APK on your device.
4. Enable the module in the LSPosed manager and make sure System Launcher is enabled in the scope settings.
5. Restart System Launcher.

(Restarting the launcher may be necessary for changes to take effect after toggling features.)