# Release Process (Android)

This document standardizes how we cut beta and stable releases for Fearless Android.

## Versioning & Branching

- Versioning: semantic with optional pre-release suffix.
  - Beta: `4.2.0-beta.1`, Stable: `4.2.0`.
  - Update and commit both `versionName` and the positive integer `versionCode`
    in `versioning/version.properties` before the release PR is merged.
  - Local release tasks retain the historical automatic `versionCode` bump for
    developer convenience. Every CI environment and the tagged release
    workflow disable that mutation; the workflow hashes the committed version
    file before building and rejects any change after the build.
- Branching:
  - Work branch: feature/stabilization or release/docs-x.y.z
  - Open a PR to `develop` (or the release branch if used), then merge to `master` when promoted.
- Tags use strict `v`-prefixed SemVer and are created only after the release
  commit has landed on `master` and Android CI has passed:
  - Beta: `git tag -s v4.2.0-beta.1 -m "fearless-Android v4.2.0-beta.1"`
  - Stable: `git tag -s v4.2.0 -m "fearless-Android v4.2.0"`

## Preconditions

- Toolchain: JDK 21, Android SDK 36 + build-tools 36.0.0, NDK r28 (and legacy r25 if needed), Rust toolchain.
- Secrets: configured via env or `local.properties` (see README / docs samples).
- Optional alignment overrides (first run mirrors): `TYPES_URL_OVERRIDE`, `DEFAULT_V13_TYPES_URL_OVERRIDE`, and debug-only `CHAINS_URL_DEBUG_OVERRIDE`. Release chain discovery is pinned.

## Pre‑Release Checklist

- Alignment print:
  - `./gradlew printPolkadotSdkAlignment`
  - Confirm effective URLs and shared_features pin (or "(not pinned)").
- Public artifact audit:
  - `bash ./scripts/test-public-artifact-provenance-audit.sh`
  - `./scripts/audit-public-artifacts.sh --strict-provenance`
  - Confirm checked-in Firebase files use the `fearless-public` placeholder
    project, no signing material is tracked, and all nine pinned binary
    checksum/source records match `docs/binary-provenance.md` exactly.
- Public dependency governance and upstream handoff:
  - `bash ./scripts/test-fearless-utils-derived-tree.sh`
  - `FEARLESS_UTILS_PATH=../fearless-utils-Android FEARLESS_UTILS_COMMIT=7500809f33243ee47ecb2ec8563fc284ac4de0d6 FEARLESS_UTILS_REPOSITORY=soramitsu/fearless-utils-Android FEARLESS_UTILS_LIBRARY_ONLY=true ./scripts/ensure-fearless-utils.sh`
  - `bash ./scripts/test-public-dependency-upstream-delta-export.sh`
  - `bash ./scripts/export-public-dependency-upstream-delta.sh --output build/reports/public-dependency-upstream-delta`
  - Review the generated manifest's exact source pin, six compatibility-module
    digests, route-governance counts, and strict-provenance commands.
- Static analysis:
  - `./gradlew detektAll`
- Unit tests + coverage:
  - `./gradlew runTest`
  - `bash ./scripts/test-unit-test-task-membership-audit.sh && bash ./scripts/audit-unit-test-task-membership.sh`
  - The membership audit requires every module with `src/test` Kotlin or Java
    sources, including `core-api`, backup, and XCM, to be in `runTest`.
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
- Choose one distribution path:
  - For rapid, manual Internal App Sharing, run
    `./gradlew :app:bundleInternalAppSharing`. Upload
    `app/build/outputs/bundle/internalAppSharing/app-internalAppSharing.aab`
    through Play Console's Internal App Sharing UI and share its generated
    link. This variant inherits release optimization/resources but is explicitly
    debug-signed and has the `-ias` version-name suffix. It is only for Internal
    App Sharing; Gradle rejects every normal Play-publisher task for this
    variant. Do not use it to validate Play App Signing, Play Integrity,
    passkey/app-link certificate association, upgrades from production, or a
    normal testing track.
    For the recorded 16 July 2026 publication, run
    `bash ./scripts/test-google-play-internal-app-sharing-publication-audit.sh`
    and
    `bash ./scripts/audit-google-play-internal-app-sharing-publication.sh --manifest-only`.
    This is a historical, offline IAS evidence contract and must not be used as
    a production or Open Testing readiness signal. On the publication
    workstation, the audit without `--manifest-only` additionally verifies the
    ignored mode-0600 URL handoff and exact local AAB.
  - For production-equivalent signing, merge and tag the candidate as described
    below, then dispatch `Android Release` with `play_testing_track=internal`,
    `alpha`, or `beta`. Set `play_release_status=completed` only when the
    reviewed testing release should become installable; the safe default is
    `draft`. `beta` becomes a link-accessible Open Testing release only after
    the corresponding Play Console track, countries, tester eligibility, and
    required review/declarations are configured and approved.
- Never run `bundleRelease` without all four explicit upload-key inputs:
  `CI_KEYSTORE_PATH`, `CI_KEYSTORE_PASS`, `CI_KEYSTORE_KEY_ALIAS`, and
  `CI_KEYSTORE_KEY_PASS`. Release and Play publishing reject missing inputs,
  symlinked key material, the Android debug keystore, and an Android Debug
  certificate.
- Production-equivalent release preflight also requires every value compiled
  for Ramp, Coinbase, MoonPay, X1, Google OAuth, RPC/explorer access, Reown,
  Dwellir, TON, and NFT Alchemy. Empty, placeholder, control-bearing,
  oversized, or malformed values fail before Gradle can build or publish the
  AAB. Internal App Sharing remains intentionally separate and may use public
  placeholders for limited UI testing.
- The tagged workflow validates every native library inside the exact release
  AAB with `verify-android-aab-native-page-alignment.py`. All four configured
  ABIs must contain the same library set, match their ELF machine type, and use
  bounded PT_LOAD segments compatible with 16 KiB pages. An informational
  `readelf` dump or a debug build is not sufficient release evidence.
- Monitor:
  - Crash/ANR, Play pre‑launch report, QA regression, dapp sessions (Reown).
- Exit criteria:
  - Crash‑free ≥ 99.5%, no P0/P1 blocking issues in core flows.

## Stable Release

- Bump version to `x.y.z` (remove `-beta.*`).
- Restore release overlays in CI:
  - `./scripts/restore-release-overlays.sh`
  - `./scripts/audit-public-artifacts.sh --release --strict-provenance`
  - Every release build requires `GOOGLE_SERVICES_RELEASE_JSON_B64`,
    `ANDROID_RELEASE_KEYSTORE_B64`, signing passwords, and publishable
    partner-provider identifiers. `PLAY_SERVICE_ACCOUNT_JSON_B64` is restored
    only when a Play testing track was explicitly selected. Server-side API
    secrets must never be injected into the Android artifact.
- After the versioned release PR is merged and the exact `master` commit has a
  completed successful Android CI push run, tag and push it (signed when the
  release operator has a configured signing identity):
  - `git tag -s vx.y.z -m "fearless-Android vx.y.z" && git push origin vx.y.z`
- Dispatch `Android Release` from `master` with `release_tag=vx.y.z`. The
  workflow rejects tags outside `master`, mismatched version names, prior CI
  from another commit/branch/event, unsigned or ambiguously selected AABs, and
  version-file mutation. It uploads the checksum/provenance manifest and emits
  a GitHub build-provenance attestation for the staged AAB.
- The optional Play step publishes that exact staged, checksum-verified,
  provenance-bound, production-signed AAB. Immediately before publication it
  revalidates the requested non-production track, service account, artifact
  digest/provenance, and upload-certificate match. An `always()` cleanup step
  removes restored Firebase, keystore, and Play credentials on both success and
  failure.
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

- Play testing track: `internal`, `alpha`, or `beta`; the tagged release workflow
  intentionally cannot select production. Use `beta` for Open Testing only
  after completing its separate Play Console setup and review.
- Production rollout remains a separate reviewed operation and is staged.
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
