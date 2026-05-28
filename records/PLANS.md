# OnePlusPlusLauncher OOS16 Plans

## Planning Rules

- Keep this file to accepted future direction only.
- Track concrete launcher compatibility work separately from optional feature work.
- Convert validated outcomes into `records/STATUS.md` and release notes.

## Approved Directions

### OOS16 Launcher 16.6.5 Compatibility Recovery

- Outcome: working keyboard focus and redirect behavior for swipe up, search-button redirect, and swipe-down redirect.
- Why this is accepted: current production behavior is broken on target device/software.
- Expected value: restores core module value and reduces user breakage reports.
- Preconditions: verified method signatures from launcher 16.6.5 and on-device LSPosed test loop.
- Earliest likely start: immediate
- Related ids: upcoming `RSH-*`

### Distribution and CI Stabilization

- Outcome: forked repository with reliable debug APK artifacts from GitHub Actions.
- Why this is accepted: hotfixes need a repeatable delivery path.
- Expected value: faster validation cycles and easier tester distribution.
- Preconditions: origin remote access and successful workflow run.
- Earliest likely start: immediate
- Related ids: none

## Sequencing

### Near Term

- Initiative:
  - Why now: unblock broken user-facing behavior on OnePlus 13.
  - Dependencies: adb device connectivity, launcher package extraction.
  - Related ids: `RSH-*` (to be created)

### Mid Term

- Initiative:
  - Why later: after hotfix validation, align release workflow and tag strategy.
  - Dependencies: signing secrets and release notes update.
  - Related ids: none

### Deferred But Accepted

- Initiative:
  - Why deferred: compose UI rewrite is non-critical versus compatibility fixes.
  - Revisit trigger: after stable 16.6.5 release and low bug backlog.
  - Related ids: none
