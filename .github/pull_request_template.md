## Summary

Describe the change and why it’s needed.

## Related Issue

Closes #<issue-number> (or) Relates to #<issue-number>

## Target Branch

- [ ] This PR targets `develop`
- [ ] This PR targets `master` and is a release or hotfix PR

## Type of Change

- [ ] feat (new feature)
- [ ] fix (bug fix)
- [ ] refactor (no functional change)
- [ ] chore/build (tooling, CI, deps)
- [ ] docs (README/AGENTS, comments)

## Screenshots / Videos

If UI changes, include before/after.

## Test Plan

Commands run locally:

```
./gradlew detektAll
./gradlew runTest
./gradlew :app:lint
./scripts/audit-public-artifacts.sh
```

Additional checks and scenarios covered:
- 

## Risks & Rollout

Potential impact, migrations, or config/secrets required.

## Checklist

- [ ] Linked an issue and added a clear description
- [ ] Added/updated tests for changed code (where applicable)
- [ ] Updated docs (README/AGENTS) when behavior or commands changed
- [ ] Ran detektAll, runTest, and :app:lint locally (or via CI)
- [ ] Public artifact audit passed; no secrets or local.properties committed
- [ ] No direct-to-`master` workflow is introduced
