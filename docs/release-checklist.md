# Release Checklist

Use this checklist for every release PR from `develop` to `master`. The Android
release process in `docs/releases/PROCESS.md` remains the detailed app-specific
procedure.

## Before The Release PR

- Confirm all release work has landed on `develop`.
- Confirm the release version and changelog entry are final.
- Confirm no private keys, store credentials, analytics tokens, or local
  environment files are committed.
- Confirm the MoonPay signing secret formerly embedded in released Android
  artifacts has been revoked/rotated with MoonPay. Keep MoonPay disabled in the
  client until URL signing is provided by a backend or a supported public
  mobile flow.
- Confirm `versioning/version.properties` contains an unused Play version code
  greater than every active, draft, and historical testing-track artifact.
  Build 230 is reserved because production is already 229.
- Run `bash scripts/test-android-release-signed-tag-policy.sh`,
  `bash scripts/test-android-release-governance.sh`,
  `bash scripts/test-android-play-release-guards.sh`,
  `bash scripts/test-android-release-aab-signer.sh`,
  `bash scripts/test-android-unsigned-release-build.sh`,
  `bash scripts/test-android-release-source-binding.sh`,
  `bash scripts/test-android-release-source-tree.sh`,
  `bash scripts/test-fearless-utils-source-integrity.sh`,
  `bash scripts/test-gradle-dependency-provenance.sh`,
  `bash scripts/test-android-moonpay-client-secret-policy.sh`,
  `bash scripts/verify-android-moonpay-client-secret-policy.sh`,
  `bash scripts/test-android-aab-jar-signature.sh`,
  `bash scripts/test-android-aab-identity.sh` against a freshly built AAB and
  the checksum-pinned bundletool,
  `bash scripts/test-android-release-build-log.sh`,
  `bash scripts/test-release-overlay-interruption.sh`,
  `bash scripts/test-android-release-media-permissions.sh`, and
  `bash scripts/verify-android-release-media-permissions.sh app/src/main/AndroidManifest.xml`.
  Confirm the MoonPay audit scans every tracked Android module source set,
  Gradle/build input, release script, and GitHub workflow rather than only the
  former wallet provider paths, and rejects all 16 adversarial fixtures.
  Confirm the release-architecture guard reports exactly 4 positive and 119
  deterministic negative/adversarial cases. It must reject a combined
  `RELEASE_OVERLAY_MODE=release`, signing credentials exposed to Gradle, every
  Play service-account/API/Gradle publisher path, reviewer overlap between
  build and signing environments, source-tree drift, unpinned Java inputs,
  weakened downloaded provenance, and reordered attestation or cleanup.
  Confirm governance reports exactly 3 positive and 43 negative cases, and the
  source-tree gate reports exactly 3 positive and 13 adversarial cases.
  Confirm the split-overlay suite reports exactly 2 split positives, 20 split
  negatives, 20 restore-interruption, 8 cleanup-interruption, and 2
  backup-integrity cases. Every TERM/SIGKILL boundary must preserve the
  checked-in Firebase placeholder and leave no signing or Play credential
  behind.
  Confirm the standalone signer reports exactly 1 positive, 31 adversarial,
  and 14 TERM/SIGKILL cases. Confirm the CI-only unsigned Gradle gate reports
  2 positive and 25 adversarial task-graph cases, and the source-binding gate
  reports 2 positive and 10 adversarial cases. Confirm fearless-utils source
  integrity reports 4 positive and 7 adversarial cases, and exact signed-AAB
  identity reports 2 positive and 135 adversarial cases. Confirm the release
  build-log suite reports 2 positive and 26 adversarial cases and rejects
  cached, skipped, or warning-bearing R8 evidence.
  Confirm Gradle dependency provenance is strict: the wrapper distribution
  checksum, verification metadata plus its digest, the root buildscript lock,
  and the production release lockfile must all match, while CI `mavenLocal()`
  and verification/lock rewrite bypasses fail closed. Its fixture suite must
  report exactly 2 positive and 30 adversarial cases. Confirm the signed-tag
  suite reports exactly 1 positive and 13 negative cases, including combined
  repository-variable, tagger, public-key, and signer replacement.
  Confirm the AAB contract derives package/version and the embedded
  `jp.co.soramitsu.fearless.RELEASE_COMMIT` value from the bundle itself,
  rejects a source-SHA mismatch, and verifies the exact current 16-file native
  inventory and checksums. Unexpected libraries, duplicate entries, and
  substitutions of sodium, sr25519, SQLCipher, or AndroidX path binaries must
  fail.
  Do not accept Play’s broad-media “continue anyway” override: the version-code
  230 release and merged manifests must contain neither `READ_MEDIA_IMAGES` nor
  `READ_MEDIA_VIDEO` and must supersede legacy open-test builds 221/109.
- Run `bash ./scripts/test-branch-flow-audit.sh && bash ./scripts/audit-branch-flow.sh`
  and confirm the release branch flow rules still pass.
- Run `./scripts/audit-public-artifacts.sh` and confirm it passes.
- Run `FEARLESS_UTILS_PATH=../fearless-utils-Android ./scripts/ensure-fearless-utils.sh`
  and confirm the public `fearless-utils-Android` checkout is still pinned.
- Run `bash ./scripts/test-public-dependency-upstream-delta-export.sh` and
  `bash ./scripts/export-public-dependency-upstream-delta.sh --output build/reports/public-dependency-upstream-delta`;
  review `build/reports/public-dependency-upstream-delta/handoff-manifest.json`
  before the release PR so the carried `fearless-utils` overlay and public
  compatibility-module hashes are ready for upstream handoff or removal.
- For release artifact hardening, run
  `./scripts/audit-public-artifacts.sh --unsigned-release --strict-provenance`
  after the Firebase-only overlay and before Gradle, then clean that overlay
  immediately after Gradle exits. In a separate clean signing context,
  revalidate the exact unsigned four-file evidence and source tree before
  restoring the signing-only overlay and invoking the signing verifier and
  standalone signer. Never combine these overlay phases.
- Run `bash ./scripts/test-todo-debt-audit.sh && bash ./scripts/audit-todo-debt.sh`
  and confirm no new TODO/FIXME/STOPSHIP debt was introduced.
- Run
  `./gradlew :public-shared-features-xcm:testDebugUnitTest --tests 'jp.co.soramitsu.xcm.XcmServiceTest' --tests 'jp.co.soramitsu.xcm.domain.XcmEntitiesFetcherTest'`
  and confirm the public XCM transfer-engine contract remains fail-closed by
  default and rejects malformed routes before backend delegation.
- Run
  `./gradlew :runtime:testDebugUnitTest --tests 'jp.co.soramitsu.runtime.multiNetwork.chain.remote.XcmLocalChainsRegistryTest'`
  and confirm the bundled native relay-asset routes between Polkadot/Kusama and
  Polkadot Asset Hub, Moonbeam, Acala, Parallel, Kusama AssetHub, Moonriver,
  Karura, and Bifrost still carry executable XCM metadata.
- Run `bash ./scripts/test-xcm-registry-metadata-audit.sh` and then
  `bash ./scripts/audit-xcm-registry-metadata.sh --require-executable --write-gap-report build/reports/xcm-registry-gap-report.json --require-route-file scripts/xcm-required-routes.tsv --require-gap-file scripts/xcm-discovery-only-routes.tsv`
  and confirm every committed XCM execution spec is valid and the required
  native relay-asset routes remain executable. Review
  `build/reports/xcm-registry-gap-report.json` and
  `scripts/xcm-discovery-only-routes.tsv`; confirm every `missingExecutionSpec`
  route is intentionally still discovery-only and categorized as `bridge-sora`,
  `non-native-asset`, or `cross-parachain-native`. Before enabling a
  non-default public XCM transfer engine for all advertised routes, also run
  `bash ./scripts/audit-xcm-registry-metadata.sh --require-executable --require-all-routes-executable`
  and confirm the registry contains real executable metadata for every
  advertised XCM route.
- Run `bash ./scripts/test-xcm-production-evidence-audit.sh` and
  `bash ./scripts/audit-xcm-production-evidence.sh` to confirm the committed
  XCM production evidence manifest is internally consistent. Before enabling
  broad production XCM, run
  `bash ./scripts/test-xcm-production-evidence-template.sh` and
  `bash ./scripts/generate-xcm-production-evidence-template.sh --output build/reports/xcm-production-evidence-template.json`
  to create the required-route evidence skeleton, update
  `scripts/xcm-production-evidence.json` to
  `status: ready`, set `releaseEnabled: true`, remove every discovery-only
  route gap, attach E2E transfer evidence for every route in
  `scripts/xcm-required-routes.tsv`, set each evidence record `androidCommit`
  to the Android release commit under validation, and run
  `bash ./scripts/audit-xcm-production-evidence.sh --require-ready`.
  For validating a tagged release from a different checkout, set
  `XCM_PRODUCTION_EXPECTED_COMMIT` to the intended 40-character Android commit
  when running the audit.
- Run `bash ./scripts/check-iroha-mobile-sdk-release-assets.sh --self-test`.
  If `IROHA_MOBILE_SDK_RELEASE_TAG` is configured for the release, also run
  `bash ./scripts/check-iroha-mobile-sdk-release-assets.sh --download --tag "$IROHA_MOBILE_SDK_RELEASE_TAG"`.
- From the workspace root, run
  `bash scripts/audit-passkey-backup-prerequisites.sh` and confirm
  `config/passkey-backup-production.json` still matches Android passkey code,
  Drive appdata storage, and the production challenge-service contract. Before
  enabling user-facing passkey backup, run the same audit with
  `PASSKEY_BACKUP_LIVE_HEALTH=1` and confirm the deployed challenge service
  passes. Keep `PASSKEY_BACKUP_ENABLED=false` unless that live release audit is
  green for the release.
- Before enabling user-facing passkey backup, confirm the Android flow includes
  Google account selection, explicit Google Drive consent, and a recovery path
  that offers restore before creating a new backup.
- Confirm public build instructions still work without private overlays.
- When the private Android overlay checkout is available, run
  `PRIVATE_OVERLAY_REPORT=build/reports/private-overlay-boundary.tsv PRIVATE_REPO_DIR=../fearless-Android-priv ./scripts/audit-private-overlay-boundary.sh`
  and confirm it passes. If it fails, use the full TSV report to move every
  listed product-code or public-config path back to the public repo before
  release.
- Run or confirm green CI for branch-flow audit, public artifact audit,
  TODO-debt audit, Iroha mobile SDK release asset contract, private-overlay
  audit self-test, detekt, unit tests, lint, and release/debug build jobs.
- Confirm Universal Wallet migrations, legacy export-only access, and supported
  network registry changes are documented in the PR.
- Confirm rollback owner, monitoring owner, and release communication channel.

## Release PR To `master`

- Open the PR from `develop` or `release/<version>` to `master`.
- Include test evidence, migration notes, release notes, and rollback notes.
- Confirm the `Branch Flow` workflow is green for the release PR.
- Confirm credential-free `release-controls` validated the signed tag, exact
  commit/tree, prior CI, governance, and adversarial suites before either
  protected stage. Confirm release CI restored only
  `GOOGLE_SERVICES_RELEASE_JSON_B64` and
  partner build inputs before the exact CI-only unsigned
  `:app:bundleRelease`. `ANDROID_RELEASE_KEYSTORE_B64` and signing passwords
  must remain absent from that job. A fresh `release-signing` runner must
  download and revalidate the exact unsigned artifact, provenance, source
  attestation, tag, and `master` before it restores the upload key. The
  standalone signer receives the key only long enough to sign and verify the
  exact unsigned AAB. Confirm the protected jobs use the exact checksum-pinned
  Temurin `21.0.12+8` archive and record its archive and Java-tool binary
  digests in provenance. No workflow job may receive a Play service-account
  credential or mutate Play.
- Confirm the release overlay backup was first written to a same-directory
  `0600` temporary file, matched the placeholder's byte length and SHA-256,
  and was atomically renamed. Cleanup must delete incomplete backup
  temporaries and must restore only the validated canonical backup.
- Confirm the tagged source is exactly
  `v<versionName>+android.<versionCode>`, the tag is annotated and signed,
  GitHub reports its signature as valid, its object has not moved, and local
  SSH verification binds it to the one fingerprint in
  `.github/release/android-tag-signer-fingerprints.txt`. Confirm the matching
  one-line OpenSSH Ed25519 public key is base64 encoded in the
  `ANDROID_RELEASE_TAG_SIGNER_PUBLIC_KEY_B64` repository variable, the exact
  master push CI is green, and the workflow-reported AAB signer SHA-256 matches
  `40:39:10:92:F5:B9:7E:78:2C:65:28:CC:57:1A:DF:5D:BE:DF:E2:D0:50:23:BA:BC:7C:4E:33:9E:58:4A:4A:9A`.
- Confirm `android-release-build` and `android-release-signing` both deny admin
  bypass, prevent self-review, require protected branches, and use the exact
  sorted User IDs in `ANDROID_RELEASE_BUILD_REVIEWER_IDS` and
  `ANDROID_RELEASE_SIGNING_REVIEWER_IDS`. Each allowlist must contain at least
  one User and the sets must be disjoint, producing two distinct environment
  approvals. Prefer at least two Users per list for reviewer availability; that
  redundancy is not a substitute for the separate build and signing approvals.
- Confirm the unsigned AAB is attested before its exact four-file artifact is
  uploaded to the signing job. Confirm the signing job revalidates that
  artifact before restoring the key, cleans the key unconditionally, verifies
  signature/identity/native inventory/media permissions from the signed AAB,
  and attests the final exact three-file artifact before upload.
- Require review and green CI before merge.
- Merge with a merge commit so the release boundary is visible.
- Create the release tag only after the merge commit is on `master`.

## Manual Play Console Release And Aftercare

- Download `fearless-android-signed-<tag>` from the final `release-signing`
  job. It must contain only `Fearless-<tag>.aab`, its `.sha256` sidecar, and
  `provenance.json`. Verify all three attestations, the checksum, exact
  provenance, signed-AAB identity/permission evidence, and pinned
  upload-certificate fingerprint before opening Play Console.
- In Play Console, open
  [Publishing overview](https://support.google.com/googleplay/android-developer/answer/9859751)
  first. Reconcile every change in “Changes not yet sent for review” and
  confirm the release contains no unrelated or unexpected ready-to-send change.
- Open the existing
  [`beta`/Open Testing track](https://support.google.com/googleplay/android-developer/answer/9845334)
  and preserve its current
  countries/regions, tester limit, tester population, and feedback settings.
  Upload only the verified final AAB; do not create a replacement track and do
  not accept the broad-media “continue anyway” override.
- Confirm Play displays the expected package, version name, version code, and
  upload certificate. Set the testing release to `Completed`/installable,
  review the release summary, and explicitly send the intended changes for
  review. Record the Play submission ID/status.
- After Google publishes the update, open
  `https://play.google.com/apps/testing/jp.co.soramitsu.fearless` using a
  tester account that was not pre-authorized, opt in, install from Google Play,
  and confirm the installed version code and cold-start wallet smoke test.
- Verify the distributed artifact matches the tagged commit.
- Monitor crash, ANR, wallet creation, import, transfer, and indexer error rates.
- Keep the hotfix path ready from `master` until rollout completes.
