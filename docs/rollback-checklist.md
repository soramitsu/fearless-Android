# Rollback Checklist

Use this when a released Android build causes a production-impacting issue.

## Rules That Do Not Bend

- Android has no downgrade. Play installs only a higher `versionCode`, and a
  lower Room schema cannot open a database that a newer build already
  migrated. A rollback is always a forward fix: a new build with a higher
  `versionCode` and the same or a higher `APP_DATABASE_VERSION`.
- `APP_DATABASE_VERSION` must be at least the highest database version any
  distributed build ever shipped. `core-db/released-database-versions.txt`
  records it per build, and `AppDatabaseVersionFloorTest` enforces it. Never
  lower or delete a line there.
- Every database a user can still hold keeps its upgrade path. The ledger's
  lowest line is the oldest release (v0.4.0, database 9);
  `EARLIEST_SUPPORTED_DATABASE_VERSION` stays at or below it, and
  `AppDatabaseVersionFloorTest` fails if it is raised to drop old migrations.
- The hotfix base is the latest shipped release tag
  (`v<versionName>+android.<versionCode>`), never `master` or `develop`,
  unless `git merge-base --is-ancestor <tag> <branch>` proves that branch
  contains the tag.
- Shipped code never calls `fallbackToDestructiveMigration*`,
  `clearAllTables(` or `deleteDatabase(`. `NoDestructiveMigrationSourceTest`
  enforces this for every module. Without a destructive fallback, a build that
  meets a newer database stops at the database startup screen and keeps the
  file; with one, Room drops every table and the user's wallet list is gone.

## Trigger

- Stop or pause staged rollout when crash, ANR, signing, migration, transfer, or
  indexer-backed read failures exceed the release threshold.
- Assign one incident owner and one communication owner.

## Immediate Actions

- Halt the staged rollout in Play Console first. Halting stops new installs; it
  does not take the build back from users who already updated, and those users
  already hold the new database version.
- Turn the affected capability off through the remote feature switches when
  that removes the issue without a new binary. Remote switches can only
  disable a capability; they never enable a compiled-off one.
- Identify the latest shipped release tag, its `versionCode`, its database
  version in `core-db/released-database-versions.txt`, and the current rollout
  percentage.
- Capture failing version, device/OS distribution, feature flags, and relevant
  backend/indexer status.

## Choosing The Hotfix Base

- Start from the latest shipped release tag. Use `master` or `develop` only
  when it contains that tag.
- Run `bash scripts/audit-no-destructive-migration.sh <base>` from a checkout
  of the latest release line. It is read-only and fails when the base can drop
  the user database or sits below the recorded database floor. Name remote
  refs such as `origin/master` explicitly after a fetch; without arguments it
  audits `HEAD` and the local `develop`, `master`, `release/*` and `hotfix/*`
  branches.
- Do not build from a base the audit rejects. A higher `versionCode` built from
  such a base installs over the release and wipes or strands the data.

## Hotfix Path

- Create `hotfix/<version-or-slug>` from the chosen base.
- Raise `versionCode` in `versioning/version.properties` above every active,
  draft and historical Play artifact, and append
  `<versionName>+<versionCode>=<APP_DATABASE_VERSION>` to
  `core-db/released-database-versions.txt`.
- Apply the smallest safe fix or revert. Revert code, never migrations: if a
  shipped migration produced bad data, add a new adjacent migration that
  repairs it. Never remove a shipped migration, edit an exported schema, or
  lower `APP_DATABASE_VERSION`.
- Run `./gradlew :core-db:testDebugUnitTest` plus the release checklist checks
  that cover the changed surface, including an upgrade from the shipped build.
- Open a PR to the release line, tag after merge, then merge the fix back into
  `develop` and the next release branch.

## After Recovery

- Document root cause, affected versions, user impact, and prevention work.
- Update release notes and the project tracker with the final disposition.

## Branch State — 2026-10-10

Read-only findings that motivated the rules above:

- `origin/master` (`24fbc9dec`, 4.0.1) is at database 73 and `origin/develop`
  and local `develop` (`38fb2f3b3`) are at 76. Both still call
  `fallbackToDestructiveMigration()`, as did the 4.2.0 production source
  (`b5e22d49d`, database 75). `master` does not contain 4.2.0 at all. A build
  from any of them would wipe every table for users on 4.2.0 or later, which is
  why the old "hotfix from `master`" instruction was removed.
- `master` holds no functional change missing from the release branch. Only
  three of its commits are outside it. `2da66a2cd` has a tree identical to
  `develop` commit `a9ca9a9d9`. The merge `b8ff31b7e` has an empty combined
  diff, so its resolution added nothing. `24fbc9dec` differs from `develop`
  commit `d3d53727d` in 21 files; `develop` took those changes back in
  `2c19919e9` ("staging to develop", #1232). What remains is import order, a
  duplicated Kotlin `compilerOptions` block in `app/build.gradle` and a
  duplicated TON Connect intent filter in the manifest. Nothing needs a
  cherry-pick.
- Local `develop` is an ancestor of `release/fearless-safe-upgrade`, so it can
  fast-forward onto the release.

Release owner steps once the release candidate is final, all local until the
push at release time:

1. Fast-forward local `develop`:
   `git fetch . release/fearless-safe-upgrade:develop`.
2. In a worktree, create `release/master-candidate` from the release branch and
   record `master`'s history without its content:
   `git merge -s ours origin/master -m "Merge master history; content = safe-upgrade release (no destructive fallback)"`.
   Leave the local `master` pointer alone; move it when pushing the release.
3. Optionally keep a local `guard/master-no-destructive-fallback` branch:
   `origin/master` plus one commit that deletes
   `.fallbackToDestructiveMigration()` and adds a top-of-file note that the
   branch is not a valid hotfix base (database 73 is below the shipped floor).
   A build from it then fails closed instead of wiping. It must never ship.
4. Run `bash scripts/audit-no-destructive-migration.sh develop release/master-candidate`
   and confirm both pass.
