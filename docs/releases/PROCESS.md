# Release Process (Android)

This document standardizes how we cut beta and stable releases for Fearless Android.

## Versioning & Branching

- Versioning: semantic with optional pre-release suffix.
  - Beta: `4.2.0-beta.1`, Stable: `4.2.0`.
  - Update and commit both values in `versioning/version.properties`. Builds
    never mutate or override the source-controlled `versionCode`.
  - Before reserving a code, inspect every Play track. The 4.2.0 migration
    candidate reserves version code 230 because production already uses 229.
- Branching:
  - Work branch: feature/stabilization or release/docs-x.y.z
  - Open a PR to `develop` (or the release branch if used), then merge to `master` when promoted.
- Tags (signed):
  - The signed-release-artifact workflow binds both Android version fields with
    `v<versionName>+android.<versionCode>`, rejects lightweight tags, requires
    GitHub to report a valid signature on the annotated tag object, and
    rechecks that the remote tag object and `master` have not moved at each
    artifact boundary.
  - Current candidate:
    `git -c gpg.format=ssh -c user.signingkey=/secure/path/release_ed25519 tag -s 'v4.2.0+android.230' -m 'Fearless Android 4.2.0 build 230'`.

## Preconditions

- Toolchain: JDK 21 plus an isolated Android SDK containing only platform 36
  revision 2 and build-tools 35.0.0. The protected release jobs install those
  two reviewed Google archives directly from the checked-in lock after exact
  size and SHA-256 checks; they do not inherit runner SDK packages or invoke
  `sdkmanager`. They install the exact Temurin `21.0.12+8` x64 archive
  from its fixed URL only after checking SHA-256
  `e4446ff06a276155697597cc0f1b15da004ff083f4964a35271ecee567177370`;
  provenance also records the Java, javac, jarsigner, and keytool binary
  digests. Full source-development builds may need Rust, but the release
  workflow consumes checksum-pinned checked-in JNI artifacts and does not
  install a mutable Rust toolchain.
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
- Distribution guards:
  - `bash scripts/test-android-release-signed-tag-policy.sh`
  - `bash scripts/test-android-release-governance.sh`
  - `bash scripts/test-android-play-release-guards.sh`
  - `bash scripts/test-android-play-release-verifier-snapshot.sh`
  - `bash scripts/test-android-release-aab-signer.sh`
  - `bash scripts/test-android-unsigned-release-build.sh`
  - `bash scripts/test-android-internal-app-sharing.sh`
  - `bash scripts/test-android-release-build-log.sh`
  - `bash scripts/test-android-release-source-binding.sh`
  - `bash scripts/test-android-release-source-tree.sh`
  - `bash scripts/test-android-release-sdk.sh`
  - `bash scripts/test-fearless-utils-source-integrity.sh`
  - `bash scripts/test-gradle-dependency-provenance.sh`
  - `bash scripts/test-android-moonpay-client-secret-policy.sh`
  - `bash scripts/verify-android-moonpay-client-secret-policy.sh`
  - `bash scripts/test-android-aab-jar-signature.sh`
  - `bash scripts/test-android-aab-identity.sh` with
    `AAB_IDENTITY_FIXTURE`, pinned `BUNDLETOOL_JAR`, and the expected
    package/version/source-commit environment
  - `bash scripts/test-release-overlay-interruption.sh`
  - `bash scripts/test-android-release-media-permissions.sh`
  - `bash scripts/verify-android-release-media-permissions.sh app/src/main/AndroidManifest.xml`
  - The MoonPay policy covers tracked source sets in every Android module,
    Gradle/build inputs, release scripts, and GitHub workflows. Its adversarial
    suite includes alternate modules, `buildSrc`, versioning properties, and
    alternate workflow files.
  - The release-architecture guard fixes exactly 4 positive and 119
    deterministic negative/adversarial cases. It rejects legacy combined
    overlays, signing credential overlap with Gradle, every Play
    service-account/API/Gradle mutation path, build/signing reviewer overlap,
    source-tree drift, mutable or substituted Java inputs, reordered
    artifact/attestation/signing phases, weakened downloaded evidence, and
    cleanup that is not unconditional.
  - Governance fixes 3 positive and 43 negative cases. The exact source-tree
    gate fixes 3 positive and 13 adversarial cases and rejects tracked, staged,
    untracked, source-like ignored, or premature nested-utils drift at each
    release boundary. The isolated Android SDK suite fixes 53 adversarial
    archive, extraction, path, metadata, and installed-tree cases.
  - The overlay interruption suite sends TERM and SIGKILL across 20 restore
    cases and 8 cleanup cases, including a partially constructed
    Firebase-placeholder backup.
    Backup construction uses a restricted same-directory temporary, verifies
    byte length, SHA-256, and exact `0600` mode, then atomically renames it.
    Cleanup deletes incomplete temporaries and restores only the canonical
    validated backup. Two independent positive phases and 20 split-overlay
    negatives prove Firebase/signing isolation and reject phase/credential
    overlap; two additional cases prove same-size content corruption and
    relaxed file permissions fail closed.
  - The unsigned-release task-graph suite fixes 2 positive and 25 adversarial
    cases; source binding fixes 2 positive and 10 adversarial cases. The
    standalone signer fixes 1 positive, 31 adversarial, and 14 TERM/SIGKILL
    cases while proving that every non-signature ZIP entry remains byte
    identical. The signed-AAB snapshot suite fixes 1 positive and 8
    adversarial cases, including four deterministic signer-output swaps and a
    private-snapshot tamper. Fearless-utils integrity fixes 4 positive and 7
    adversarial cases. The build-log suite fixes 2 positive and 26 adversarial
    cases and rejects cached, skipped, duplicated, warning-bearing, oversized,
    symlinked, or mutating evidence. Gradle dependency provenance fixes 2
    positive and 40
    adversarial cases and pins the Gradle distribution checksum, strict
    verification metadata and its digest, the strict root buildscript lock, and
    the production release lockfile; release/CI builds reject `mavenLocal()` and
    provenance rewrite bypasses. The signed-tag policy fixes 1 positive and 13
    negative cases, including replacement of mutable repository variables and
    the signing key.
  - Confirm the registered upload-certificate fingerprint in
    `scripts/verify-android-play-release.sh`; never substitute an AAB upload-key
    fingerprint for the separate Play App Signing fingerprint.
  - The AAB verifier requires its manifest to embed the exact tagged 40-character
    source commit, enforces the compiled network-security configuration and
    explicit cleartext denial, checks every current native entry, and rejects
    ELF magic in any ZIP entry outside the exact 16-library allowlist. Its 135
    negative cases cover identity and source mismatch, unsafe or ambiguous
    network policy, missing/extra/duplicate payloads, dynamic-module injection,
    disguised ELF assets, renamed ELF binaries, and substitutions of sodium,
    sr25519, SQLCipher, and AndroidX path binaries.
    The separate JAR-signature suite verifies the exact pinned self-signed
    upload certificate through an ephemeral truststore and rejects unsigned
    entries, tampering, the wrong certificate, multiple signers, and duplicate
    ZIP entries.
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

## Internal App Sharing Smoke Lane

> [!WARNING]
> Internal App Sharing is not a production migration test. The AAB uses the
> Android debug certificate and Google re-signs the uploaded IAS file, so it
> cannot upgrade an app installed from Google Play. Never uninstall a funded
> wallet to make the IAS build install. Never cite IAS as evidence for
> production database migration behavior or production release signing.

This lane exists only for broad, link-based smoke testing before the separately
governed Play release process. Pull-request runs are non-distributable
validation only and create no handoff or uploaded artifact. The sole eligible
handoff is a manual first-party run from exactly `refs/heads/develop`, after the
reviewed code is merged, while the branch is protected and the candidate SHA
still equals the fetched remote `develop` head. Neither Gradle nor CI uploads to
Google/Play, invokes the Android Publisher API, or creates, promotes, pauses, or
otherwise changes a Play track.

The build contract is fail-closed:

- Only CI may produce the bundle, and Gradle accepts only the exact requested
  task list `[:app:bundleInternalAppSharing]` with `--no-build-cache` and
  `--rerun-tasks`. Do not append verification, cleanup, assemble, publish, or
  other tasks to that invocation. The exact standalone configuration probe is
  `:app:verifyInternalAppSharingConfiguration`.
- `CI` must be exactly `true`; `RELEASE_COMMIT` must be the lowercase
  40-character clean checked-out `HEAD`; and `FEARLESS_UTILS_COMMIT` plus
  `FEARLESS_UTILS_EFFECTIVE_TREE` must match the independently verified utils
  checkout. Source drift fails before the artifact is created.
- The variant inherits release minification, resource shrinking, application
  ID, source-bound manifest, and version code, adds only the `-ias`
  version-name suffix, and selects a run-bound ephemeral JKS with the Android
  Debug identity. The workflow derives and binds that run's uppercase
  certificate SHA-256, keeps the JKS mode `0600`, and deletes it after the run.
  It must not modify the `release` build type or its upload-key configuration.
- Every production keystore/password or injected Android signing input is
  forbidden. So are the unsigned-release switch, Play service-account and
  publisher controls, production Firebase overlay variables, and other
  distribution credentials.
- `processInternalAppSharingGoogleServices` exposes a typed JSON input that is
  finalized directly to the checksum-pinned reviewed `fearless-public` file at
  `app/src/release/google-services.json`. Symlink traversal, hardlinks, byte
  changes, non-public Firebase values, and attempts to replace that finalized
  input fail closed. No copy, owner marker, or cleanup lifecycle exists:
  `app/src/internalAppSharing` must never be created.
- The placeholder's fake/empty Firebase and OAuth values deliberately disable
  live Firebase, Google OAuth/sign-in, and Google Drive/passkey backup and
  restore. Supported IAS smoke scope is install/fresh launch, cold start,
  onboarding/navigation, local-only disposable-wallet flows, and screens that
  do not depend on those services. Play upgrade, production migration, and
  production signing remain unsupported.
- The run-bound signer is mode `0600`, has 30-day validity, and is deleted
  unconditionally. The uploaded handoff artifact is retained for seven days;
  the runner's signer and handoff directories are removed immediately after
  upload, and are also removed on failure.
- The workflow/verifier hardcodes `4.2.0-ias` / `230`. A change to either value
  in `versioning/version.properties` requires every IAS version hardcode and
  the handoff filename to change in the same reviewed commit. Old bytes must
  never be relabeled.

The supported verifier invocation is workflow-only, under `CI=true` and the
source-bound `RELEASE_COMMIT`. It requires the checksum-pinned
`BUNDLETOOL_JAR`, exact uppercase
`EXPECTED_IAS_DEBUG_CERT_SHA256`, mode-`0600`
`ANDROID_IAS_DEBUG_KEYSTORE_PATH`, and exact `FEARLESS_UTILS_COMMIT`,
`FEARLESS_UTILS_EFFECTIVE_TREE`, and `FEARLESS_UTILS_PATH`. Its positional
arguments are the fresh AAB, expected source commit, and, only for the eligible
manual run, an absolute private handoff path.

CI qualification runs in this order:

1. Gate the exact first-party repository and supported event/ref, then check out
   and bind the exact app commit/tree and pinned utils commit/effective tree.
2. Install Java, checksum-pinned bundletool, Android SDK/NDKs, and Rust targets;
   run the static and full IAS Gradle suites.
3. Remove prior IAS outputs, prove the source clean, and invoke only the exact
   source-bound unsigned
   `:app:bundleInternalAppSharing --no-build-cache --rerun-tasks`. Recheck its
   exact outputs, production-output snapshot, app source, and utils.
4. Only for an eligible manual run, upload the verified unsigned AAB as a
   one-day untrusted producer quarantine. A fresh qualifier downloads it by
   exact artifact ID and digest, revalidates it under a disposable user, kills
   that user, externally signs with a step-local JKS, exports the public
   certificate, erases the key, and kills the signer user.
5. Under a separate public-certificate-only disposable user, run
   `scripts/test-android-internal-app-sharing-aab.sh` and the signed-from
   verifier against the exact unsigned/signed pair. Kill that user before
   creating the private exact-byte handoff.
6. Upload the handoff with the explicit pending/untrusted name, download the
   exact archive back by artifact ID and digest, enforce ZIP entry/type/size
   bounds, and revalidate its contents under another disposable user.
7. Let the separate finalizer retain the pending-name artifact only when the
   qualifier completed and the protected `develop` head is unchanged;
   otherwise it must delete it. The scheduled janitor removes canceled, failed,
   stale, or ambiguous pending artifacts.

Frozen totals are dependency provenance 2 positive / 40 adversarial, bounded
Gradle coverage cleanup 4 positive / 12 negative/adversarial, IAS Gradle 8
positive / 39 behavioral negative / 624 static adversarial assertions, and
workflow Linux public-certificate IAS AAB 7 positive / 47 adversarial
artifacts. Local AAB expectations are 7/45 on macOS in certificate mode, 7/53
on macOS in keystore mode, and 7/55 on Linux in keystore mode. Any total drift
requires review. IAS handoff status remains pending until the eligible manual
run supplies full green evidence.

The eligible manual `android-internal-app-sharing.yml` run may retain the
verified AAB and its integrity evidence only as a seven-day pending-name GitHub
Actions artifact whose exact ID/digest archive passed download-back
qualification and whose separate finalizer job succeeded. An authorized Play
Console operator may download that handoff, manually upload only the exact
verified AAB to **Internal App Sharing**, and distribute the URL Google
returns. Never consume PR-run output or an artifact from a run whose qualifier
or finalizer did not succeed. Do not upload it to Open Testing, Closed Testing,
Production, or any other track. Record the workflow run, commit,
trusted-develop eligibility marker, artifact ID/digest, AAB SHA-256, run-bound
signer fingerprint, verifier output, IAS upload identity, and generated URL in
the test evidence.

Production upgrade/migration qualification remains separate: use a disposable,
unfunded test wallet installed through Google Play and upgrade it with a newer
Play-signed candidate through the intended Play testing track. Preserve the
original app data and exercise the documented migration, recovery, and
cold-start checks. IAS results may supplement feature smoke testing only.

## Beta Release

- Merge the exact reviewed candidate through `develop` to `master`, wait for
  the exact master push CI, then create the immutable version/code tag.
- Dispatch `Android Signed Release Artifact` from the tagged `master` commit.
  The
  workflow has no Play track, status, or publication input and never mutates
  Play.
- Credential-free `release-controls` first validates the signed tag, exact
  remote `master` commit/tree, prior push CI, disjoint protected-environment
  governance, the source tree, and every adversarial release suite. It has no
  environment or repository secret.
- `release-build` runs on a fresh runner protected by
  `android-release-build`. It restores only the production Firebase overlay,
  invokes the exact source-bound
  `ANDROID_UNSIGNED_RELEASE_BUILD=true ./gradlew :app:bundleRelease` path, and
  receives no signing or Play credential. It verifies the unsigned bundle,
  stages exactly the AAB, checksum sidecar, exact-key-set unsigned provenance,
  and bounded verified Gradle/R8 build log; individually attests all four files,
  verifies all four attestations, and only then uploads the artifact.
- `release-signing` runs on a second fresh runner, needs `release-build`, and is
  separately protected by `android-release-signing`. Before restoring the
  upload key it downloads exactly those four files, re-hashes them, validates
  every provenance field, re-verifies all four source-bound attestations, and
  rechecks the signed tag, current `master`, and exact source tree. It then
  invokes the standalone
  certificate-pinned signer. The signer snapshots the expected unsigned
  digest, proves every non-signature ZIP entry is byte-identical, verifies the
  registered upload certificate, and atomically writes the signed AAB.
- The signing job removes its key unconditionally, captures the digest emitted
  for the signer's private verified AAB, and treats that digest as the sole
  signed-byte authority. Signature and package/version/source/native/permission
  identity checks consume one private snapshot of those bytes. The workflow
  records the identity output, identity-output digest, and exact
  merged-permission digest in final provenance; requires the staged AAB,
  checksum sidecar, provenance, attestations, and immediate pre-upload gate to
  match the trusted signer digest; and only then uploads
  `fearless-android-signed-<tag>` for the operator.
- Both protected jobs use the exact independently checksum-pinned Temurin
  `21.0.12+8` archive. Unsigned and final provenance bind its archive plus
  Java-tool binary digests. The signing alias comes from
  `ANDROID_RELEASE_KEY_ALIAS`, a reviewed non-secret repository variable; only
  the keystore and passwords are secrets.
- Both environments must deny admin bypass, prevent self-review, and allow only
  protected branches. Configure exact sorted User IDs in
  `ANDROID_RELEASE_BUILD_REVIEWER_IDS` and
  `ANDROID_RELEASE_SIGNING_REVIEWER_IDS`; each list requires at least one User,
  and the lists must be disjoint so the artifact crosses two distinct
  environment approvals. Prefer two or more Users per list for availability,
  but do not treat that redundancy as a second approval within one
  environment. Replace the fail-closed sentinel in
  `.github/release/android-tag-signer-fingerprints.txt` with the one exact
  `SHA256:...` value emitted by `ssh-keygen -l -E sha256 -f <signer.pub>`, and
  set the `ANDROID_RELEASE_TAG_SIGNER_PUBLIC_KEY_B64` repository variable to
  one-line base64 of that matching OpenSSH Ed25519 public-key line. Configure
  `ANDROID_RELEASE_TAGGER_EMAIL` as defense-in-depth metadata only; it is not a
  signer trust anchor. CI receives no Play service-account credential.
- An authorized operator downloads and verifies the final workflow artifact,
  then opens Play Console’s
  [Publishing overview](https://support.google.com/googleplay/android-developer/answer/9859751).
  Reconcile every ready-to-send/pending change before opening the existing
  [`beta`/Open Testing release](https://support.google.com/googleplay/android-developer/answer/9845334).
  Upload only the verified final AAB, preserve the existing countries/regions,
  tester limit/population, and feedback settings, select
  `Completed`/installable, review the summary, and explicitly send only the
  intended changes for review. Record the submission ID/status.
- The existing Open Testing URL is
  `https://play.google.com/apps/testing/jp.co.soramitsu.fearless`. After Google
  publishes the update, use a tester account that was not pre-authorized to
  opt in through this link, install from Google Play, verify version code 230,
  and perform a cold-start wallet smoke test. The test link can take time to
  update after first publication.
- Do not use Play’s “continue anyway” media-permission override. Submit build
  230, which contains neither `READ_MEDIA_IMAGES` nor `READ_MEDIA_VIDEO`, with
  the beta-track resume so it supersedes legacy version codes 221 and 109.
- Monitor:
  - Crash/ANR, Play pre‑launch report, QA regression, dapp sessions (Reown).
- Exit criteria:
  - Crash‑free ≥ 99.5%, no P0/P1 blocking issues in core flows.

## Stable Release

- Bump version to `x.y.z` (remove `-beta.*`).
- Use the same three-job chain in CI: credential-free `release-controls`,
  protected `release-build`, then protected `release-signing`.
  - In `release-controls`, validate the signed tag, prior exact-head CI,
    governance, adversarial suites, and exact source commit/tree without
    restoring any environment or secret.
  - In `release-build`, before Gradle:
    `RELEASE_OVERLAY_MODE=firebase ./scripts/restore-release-overlays.sh`,
    followed by
    `./scripts/audit-public-artifacts.sh --unsigned-release --strict-provenance`.
    Run only the exact source-bound CI unsigned bundle task, attest its exact
    four-file evidence, and clean the Firebase overlay unconditionally.
  - In the fresh `release-signing` job, revalidate the downloaded unsigned
    evidence and source before running
    `RELEASE_OVERLAY_MODE=signing ./scripts/restore-release-overlays.sh`,
    followed by the signing-config verifier and standalone AAB signer. Clean
    the key unconditionally, verify and attest the final exact three-file
    evidence, then upload the workflow artifact.
  - Always run `CI=true ./scripts/cleanup-release-overlays.sh` in a final
    cleanup step, including cancelled and failed jobs.
  - Required CI secrets include `GOOGLE_SERVICES_RELEASE_JSON_B64`,
    `ANDROID_RELEASE_KEYSTORE_B64`, signing passwords, and partner-provider
    keys. Configure the non-secret signing alias in
    `ANDROID_RELEASE_KEY_ALIAS`. No Play service-account secret is accepted.
- Tag and push (SSH-signed with the private key matching the checked-in
  fingerprint):
  - `git -c gpg.format=ssh -c user.signingkey=/secure/path/release_ed25519 tag -s 'v<x.y.z>+android.<code>' -m 'Fearless Android <x.y.z> build <code>'`
  - `git push origin 'v<x.y.z>+android.<code>'`
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

- The workflow produces a signed, attested upload artifact but never mutates a
  Play track. For this testing release, upload manually to the existing
  `beta`/Open Testing track. Production requires a separate reviewed promotion.
- Signing: the standalone CI upload-key signer must match the pinned Play
  upload certificate; Google Play App Signing remains the distribution signer.
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
