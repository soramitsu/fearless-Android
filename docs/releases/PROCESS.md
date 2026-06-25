# Release Process (Android)

This document standardizes how we cut beta and stable releases for Fearless Android.

## Versioning & Branching

- Versioning: semantic with optional pre-release suffix.
  - Beta: `4.2.0-beta.1`, Stable: `4.2.0`.
  - Update `versioning/version.properties` → `versionName=...`.
  - `versionCode` is auto-bumped whenever `:app:assembleRelease`/`bundleRelease`/publish release tasks run (unless `CI_BUILD_ID` is provided). Commit the updated `versioning/version.properties` after the release artifact is generated so the new code is tracked.
- Branching:
  - Work branch: feature/stabilization or release/docs-x.y.z
  - Open a PR to `develop` (or the release branch if used), then merge to `master` when promoted.
- Tags (signed):
  - Beta: `git tag -s 4.2.0-beta.1 -m "fearless-Android 4.2.0-beta.1"`
  - Stable: `git tag -s 4.2.0 -m "fearless-Android 4.2.0"`

## Preconditions

- Toolchain: JDK 21, Android SDK 36 + build-tools 36.0.0, NDK r28 (and legacy r25 if needed), Rust toolchain.
- Secrets: configured via env or `local.properties` (see README / docs samples).
- Optional alignment overrides (first run mirrors): `TYPES_URL_OVERRIDE`, `DEFAULT_V13_TYPES_URL_OVERRIDE`, `CHAINS_URL_OVERRIDE`.

## Pre‑Release Checklist

- Alignment print:
  - `./gradlew printPolkadotSdkAlignment`
  - Confirm effective URLs and shared_features pin (or "(not pinned)").
- Public artifact audit:
  - `./scripts/audit-public-artifacts.sh`
  - Confirm checked-in Firebase files use the `fearless-public` placeholder
    project, no signing material is tracked, and pinned binary checksums match.
- Static analysis:
  - `./gradlew detektAll`
- Unit tests + coverage:
  - `./gradlew runTest`
  - Inspect `*/build/reports/tests/testDebugUnitTest/index.html`.
- Lint (app):
  - `./gradlew :app:lint`
- Full sequence (fast‑fail ordered):
  - `./gradlew postMergeVerify`
- Update docs:
  - `CHANGELOG.md` with a concise, user‑facing summary.
  - `docs/releases/<version>.md` with scope, risks, test matrix, rollout.

## Beta Release

- Bump version to `x.y.z-beta.n`.
- Build:
  - `./gradlew :app:assembleRelease`
- Upload (CI recommended):
  - Use Gradle Play Publisher or your CI step to release to an internal/closed track.
- Monitor:
  - Crash/ANR, Play pre‑launch report, QA regression, dapp sessions (Reown).
- Exit criteria:
  - Crash‑free ≥ 99.5%, no P0/P1 blocking issues in core flows.

## Stable Release

- Bump version to `x.y.z` (remove `-beta.*`).
- Restore release overlays in CI:
  - `./scripts/restore-release-overlays.sh`
  - `./scripts/audit-public-artifacts.sh --release --strict-provenance`
  - Required CI secrets include `GOOGLE_SERVICES_RELEASE_JSON_B64`,
    `ANDROID_RELEASE_KEYSTORE_B64`, `PLAY_SERVICE_ACCOUNT_JSON_B64`, signing
    passwords, and partner-provider keys.
- Tag and push (signed):
  - `git tag -s x.y.z -m "fearless-Android x.y.z" && git push origin x.y.z`
- Staged rollout:
  - 10% → 25% → 50% → 100%, monitoring crash‑free and error rates.
- Post‑release:
  - Create `x.y.z` GitHub Release notes (paste from changelog).
  - Consider `x.y.(z+1)` patch branch if hotfixes expected.

## CI/CD Integration

- GitHub Actions:
  - Runs detekt, tests, lint, assemble. Prints Polkadot SDK alignment early.
- Jenkins (PRs):
  - `testCmd: runTest` for unit tests; use `postMergeVerify` on demand for full checks.
- Stability guards:
  - Ordered tasks to avoid DataBinding races; Gradle parallel disabled in Jenkins.

## Store Submission Notes

- Play track: Beta to internal/closed; stable to production (staged).
- Signing: CI or Play App Signing as configured.
- Release text: summarized from `CHANGELOG.md` and `docs/releases/<version>.md`.

## Rollback

- Halt staged rollout in Play if metrics degrade.
- Revert tag and bump hotfix `x.y.(z+1)` if needed.
- Communicate in PR and `docs/status.md`.

## PR Template (Release Docs or Bump)

- Use the repository PR checklist; include:
  - Version changes, tags planned.
  - Links to release docs and changelog.
  - Evidence of detekt/tests/lint runs (local or CI).
