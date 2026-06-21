# SearchUp OOS16 Status

## Snapshot

- Last updated: 2026-06-21
- Overall posture: `active`
- Current focus: Keep the SearchUp default flow simple and verify it against target launcher updates.
- Highest-priority blocker: none known in this checkout.
- Next operator decision needed: whether to ship the settings-copy cleanup as the next release.
- Related decisions: none

## Current State Summary

SearchUp has been rebranded and released for the OOS16 launcher track. The core value is swipe-up search focus, Korean-aware fuzzy search, Enter-to-launch, redirect handling, gesture-back cleanup, and an in-app launcher restart action.

## Active Phases Or Tracks

### SearchUp Flow Polish

- Goal: Make the settings screen explain the recommended SearchUp flow first, with redirect/focus edge toggles treated as advanced controls.
- Status: `in progress`
- Why this matters now: the feature set works best when users can tell which toggles are the main experience.
- Current work: UI wording and grouping cleanup.
- Exit criteria: app settings surface separates the recommended flow from advanced redirects.
- Dependencies: local build and device sanity check before release.
- Risks: copy-only changes can still leave users confused if LSPosed scope is wrong.
- Related ids: none

### Launcher Compatibility Watch

- Goal: Keep verified compatibility claims tied to real System Launcher versions.
- Status: `active`
- Why this matters now: OEM launcher updates can break hook signatures.
- Current work: preserve safe fallback behavior and update release notes when launcher support changes.
- Exit criteria: each public compatibility claim names the tested launcher/device base.
- Dependencies: adb/LSPosed validation loop on target hardware.
- Risks: launcher internals can drift without warning.
- Related ids: none
