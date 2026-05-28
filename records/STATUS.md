# OnePlusPlusLauncher OOS16 Status

## Snapshot

- Last updated: 2026-05-28
- Overall posture: `active`
- Current focus: System Launcher 16.6.5 compatibility fix for keyboard and auto focus paths.
- Highest-priority blocker: Launcher 16.6.5 internal API drift versus 16.4.15 assumptions.
- Next operator decision needed: release tag naming and whether to publish test builds before full release.
- Related decisions: none

## Current State Summary

The fork has been initialized from upstream and adopted into a repo-template structure. The immediate execution track is to repair auto focus and keyboard behavior on OnePlus 13 OxygenOS 16 with System Launcher 16.6.5, then validate build automation through GitHub Actions artifact output.

## Active Phases Or Tracks

### 16.6.5 Compatibility Hotfix

- Goal: Restore keyboard and auto focus behavior for swipe-up and swipe-down redirect paths.
- Status: `in progress`
- Why this matters now: The current release is functionally broken on target launcher version.
- Current work: Hook signature verification, focus logic unification, and swipe-down preference wiring.
- Exit criteria: All three affected toggles work on-device and debug APK builds in Actions.
- Dependencies: adb access, LSPosed test loop, decompiled launcher references.
- Risks: OEM launcher updates may invalidate new signatures quickly.
- Related ids: pending `RSH-*`

### CI and Fork Hardening

- Goal: Ensure devuterian fork is pushable and consistently builds APK artifacts.
- Status: `in progress`
- Why this matters now: Fixes must be distributable and reproducible.
- Current work: remote setup, push flow, workflow trigger validation.
- Exit criteria: successful Actions run with downloadable APK artifact.
- Dependencies: GitHub auth, origin remote permissions.
- Risks: unauthenticated CLI session and release secret setup delays.
- Related ids: none
