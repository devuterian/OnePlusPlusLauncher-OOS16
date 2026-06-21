# SearchUp OOS16 Plans

## Planning Rules

- Keep this file to accepted future direction only.
- Track concrete launcher compatibility work separately from optional feature work.
- Convert validated outcomes into `records/STATUS.md` and release notes.

## Approved Directions

### Settings Surface Simplification

- Outcome: the app settings screen presents the recommended SearchUp flow first and keeps redirects/focus edge cases as advanced controls.
- Why this is accepted: the module's value is simple, but the old settings surface exposed implementation details too early.
- Expected value: less setup confusion and fewer wrong-toggle states.
- Preconditions: local build and quick on-device sanity check.
- Earliest likely start: immediate
- Related ids: none

### Distribution Sanity

- Outcome: release notes and APK naming match the SearchUp brand and tested launcher version.
- Why this is accepted: users should not have to infer whether a build is current for their launcher.
- Expected value: clearer upgrades and easier rollback when launcher updates break hooks.
- Preconditions: release build plus target-device validation.
- Earliest likely start: immediate
- Related ids: none

## Sequencing

### Near Term

- Initiative: finish settings wording/grouping cleanup.
  - Why now: it is the cheapest way to make the existing feature set feel intentional.
  - Dependencies: local build.
  - Related ids: none

### Mid Term

- Initiative: keep compatibility notes current per launcher release.
  - Why later: only matters when a new launcher build or public APK is shipped.
  - Dependencies: adb device validation and release notes update.
  - Related ids: none

### Deferred But Accepted

- Initiative: full settings-app redesign.
  - Why deferred: current XML screen is enough after copy and grouping cleanup.
  - Revisit trigger: repeated user confusion that wording cannot fix.
  - Related ids: none
