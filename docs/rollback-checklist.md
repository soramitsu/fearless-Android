# Rollback Checklist

Use this when a released Android build causes a production-impacting issue.

## Trigger

- Stop or pause staged rollout when crash, ANR, signing, migration, transfer, or
  indexer-backed read failures exceed the release threshold.
- Assign one incident owner and one communication owner.

## Immediate Actions

- Identify the last known-good `master` tag and current rollout percentage.
- Capture failing version, device/OS distribution, feature flags, and relevant
  backend/indexer status.
- Disable remote config or feature gates first when that removes the issue
  without a binary rollback.
- If binary rollback is required, halt rollout in the store console and prepare a
  hotfix branch from `master`.

## Hotfix Path

- Create `hotfix/<version-or-slug>` from `master`.
- Apply the smallest safe fix or revert.
- Run the release checklist checks that cover the changed surface.
- Open a PR to `master`, tag after merge, then merge or cherry-pick back to
  `develop`.

## After Recovery

- Document root cause, affected versions, user impact, and prevention work.
- Update release notes and the project tracker with the final disposition.
