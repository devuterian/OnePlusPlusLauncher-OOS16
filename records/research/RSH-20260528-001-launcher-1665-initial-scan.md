# RSH-20260528-001: Launcher 16.6.5 Initial Compatibility Scan

Opened: 2026-05-28 23-15-00 KST
Recorded by agent: codex

## Question

What likely changed between launcher 16.4.15 assumptions and launcher 16.6.5 behavior for keyboard and auto-focus hooks?

## Findings

- The module currently declares `SUPPORTED_LAUNCHER_VERSION` as `16.4.15`.
- `SwipeDownSearchRedirectHook` had UI preference wiring for swipe-down auto focus, but the hook path intentionally skipped calling `HookUtils.focusSearchInput`.
- `GlobalSearchRedirectHook` duplicated a private focus implementation instead of using the shared retry-based focus utility.
- `SwipeUpAutoFocusHook` only hooked `onStateSetStart`, which is fragile when launcher transitions move to a different callback in newer builds.
- `HookUtils.focusSearchInput` is robust for IME retries, but manager lookup depended on `getSearchUiManager` only.

## Evidence

- `app/src/main/java/com/wizpizz/onepluspluslauncher/hook/features/SwipeDownSearchRedirectHook.kt`
- `app/src/main/java/com/wizpizz/onepluspluslauncher/hook/features/GlobalSearchRedirectHook.kt`
- `app/src/main/java/com/wizpizz/onepluspluslauncher/hook/features/SwipeUpAutoFocusHook.kt`
- `app/src/main/java/com/wizpizz/onepluspluslauncher/hook/features/HookUtils.kt`
- `app/build.gradle.kts`

## Device Extraction Status

- Tried `adb kill-server`, `adb start-server`, and `adb devices -l`.
- No connected device was visible at execution time, so APK extraction and direct 16.6.5 decompile verification is pending.

## Next Steps

- Reconnect phone and authorize adb.
- Pull launcher APK from `com.android.launcher`.
- Validate method/class signatures used by swipe-up, swipe-down, and search manager hooks.
