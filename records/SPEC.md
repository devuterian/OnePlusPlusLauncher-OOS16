# SearchUp OOS16 Spec

- Project: SearchUp / OnePlusPlusLauncher OOS16 (devuterian fork)
- Canonical repo: https://github.com/devuterian/OnePlusPlusLauncher-OOS16
- Upstream repo: https://github.com/zhangbaoshengrio/OnePlusPlusLauncher-OOS16
- Project id: oenpluspluslauncher
- Operator: devuterian
- Last updated: 2026-06-21

## Project Thesis

SearchUp is an LSPosed module that turns the OnePlus OxygenOS 16 app drawer into a fast launcher search surface: swipe up, type immediately, and launch the intended app with minimal friction.

## Core Capabilities

- Auto focus and keyboard display when entering app drawer via swipe up.
- Korean-aware fuzzy search, including Hangul syllable and initial-consonant matching.
- Launch the first visible search result on Enter.
- One-tap System Launcher restart after settings changes.
- Redirect global search entry points to app drawer search.
- Redirect swipe-down search to app drawer with optional auto focus.
- Redirect left-swipe Discover entry to app drawer search when enabled.

## Invariants

- Must remain scoped to `com.android.launcher` under LSPosed.
- Must not commit signing secrets or local `.env` files.
- Hook behavior must degrade safely when launcher internals change.
- Compatibility claims must track verified launcher versions.

## Non-goals

- Full compatibility with non-OnePlus launchers.
- Feature parity with unrelated launcher mods.
- A full settings-app redesign beyond making the core SearchUp flow obvious.
