# OnePlusPlusLauncher OOS16 Spec

- Project: OnePlusPlusLauncher OOS16 (devuterian fork)
- Canonical repo: https://github.com/devuterian/OnePlusPlusLauncher-OOS16
- Upstream repo: https://github.com/zhangbaoshengrio/OnePlusPlusLauncher-OOS16
- Project id: oenpluspluslauncher
- Operator: devuterian
- Last updated: 2026-05-28

## Project Thesis

This project maintains an LSPosed module for OnePlus System Launcher on OxygenOS 16, with a focus on preserving search-entry ergonomics (auto keyboard focus, redirect behavior, and search launch flow) across launcher updates.

## Core Capabilities

- Auto focus and keyboard display when entering app drawer via swipe up.
- Redirect global search entry points to app drawer search.
- Redirect swipe-down search to app drawer with optional auto focus.
- Launch first search result on Enter.
- Optional fuzzy search for ranking app results.

## Invariants

- Must remain scoped to `com.android.launcher` under LSPosed.
- Must not commit signing secrets or local `.env` files.
- Hook behavior must degrade safely when launcher internals change.
- Compatibility claims must track verified launcher versions.

## Non-goals

- Full compatibility with non-OnePlus launchers.
- Feature parity with unrelated launcher mods.
- Rewriting the module UI during compatibility hotfix work.
