# Release Checklist

Use this checklist for every release PR from `develop` to `master`. The Android
release process in `docs/releases/PROCESS.md` remains the detailed app-specific
procedure.

## Before The Release PR

- Confirm all release work has landed on `develop`.
- Confirm the release version and changelog entry are final.
- Confirm no private keys, store credentials, analytics tokens, or local
  environment files are committed.
- Confirm MoonPay configuration contains only publishable keys. The Android
  app must not receive a MoonPay API secret or sign `walletAddress` URLs on the
  client; prefilled wallet URLs require a separately reviewed backend signer.
- Run `bash ./scripts/test-branch-flow-audit.sh && bash ./scripts/audit-branch-flow.sh`
  and confirm the release branch flow rules still pass.
- Run
  `bash ./scripts/test-public-artifact-provenance-audit.sh && ./scripts/audit-public-artifacts.sh --strict-provenance`
  and confirm the exact nine-artifact checksum/source contract passes.
- Run `bash ./scripts/test-fearless-utils-derived-tree.sh`, then run
  `FEARLESS_UTILS_PATH=../fearless-utils-Android FEARLESS_UTILS_COMMIT=7500809f33243ee47ecb2ec8563fc284ac4de0d6 FEARLESS_UTILS_REPOSITORY=soramitsu/fearless-utils-Android FEARLESS_UTILS_LIBRARY_ONLY=true ./scripts/ensure-fearless-utils.sh`.
  Confirm the public `fearless-utils-Android` checkout has the exact expected
  origin, pinned commit, clean index, and deterministic committed-overlay tree;
  do not release with any reported tracked, untracked, or submodule drift.
- Run `bash ./scripts/test-public-dependency-upstream-delta-export.sh` and
  `bash ./scripts/export-public-dependency-upstream-delta.sh --output build/reports/public-dependency-upstream-delta`;
  review `build/reports/public-dependency-upstream-delta/handoff-manifest.json`
  before the release PR so the carried `fearless-utils` overlay and public
  compatibility-module hashes are ready for upstream handoff or removal.
- For release artifact hardening, restore private overlays and run
  `./scripts/audit-public-artifacts.sh --release --strict-provenance`; the
  Android Release workflow runs the same strict provenance gate before building
  the release artifact.
- Run
  `bash ./scripts/test-release-workflow-audit.sh && bash ./scripts/audit-release-workflow.sh`
  and confirm release inputs, exact-tag/master ancestry, authoritative prior
  Android CI, immutable versioning, AAB signer matching, pinned actions, and
  build-provenance attestation remain mandatory.
- Run
  `bash ./scripts/test-google-play-internal-app-sharing-publication-audit.sh`
  and
  `bash ./scripts/audit-google-play-internal-app-sharing-publication.sh --manifest-only`.
  Confirm the 16 July 2026 Internal App Sharing record remains bound to the
  exact test-only AAB digest and URL digest without committing the unlisted
  link. This historical record is not production/Open Testing readiness and
  must keep every production, signer, Integrity, app-link, Firebase, upgrade,
  device-install, and independent-tester classifier false.
- Run
  `bash ./scripts/test-android-distribution-preflight.sh && bash ./scripts/test-restore-release-overlays.sh && bash ./scripts/test-cleanup-release-overlays.sh`.
  Confirm production signing rejects missing/debug/symlinked credentials,
  testing publication accepts only `internal`, `alpha`, or `beta`, staged AAB
  digest/provenance/signer drift fails, release overlays are transactional, and
  malformed cleanup sentinels fail closed without deleting unrelated files.
- Run `bash ./scripts/test-android-aab-native-page-alignment.sh`. On the exact
  release AAB, run
  `python3 ./scripts/verify-android-aab-native-page-alignment.py <release.aab>`
  and require all native libraries, all four configured ABIs, ELF machine
  identities, PT_LOAD bounds, and 16 KiB offset/address alignment to pass. The
  old informational `readelf` log is not release evidence.
- Run
  `bash ./scripts/test-unit-test-task-membership-audit.sh && bash ./scripts/audit-unit-test-task-membership.sh`
  and confirm every source-backed unit-test module is covered by `runTest`.
- Run `bash ./scripts/test-todo-debt-audit.sh && bash ./scripts/audit-todo-debt.sh`
  and confirm no new TODO/FIXME/STOPSHIP debt was introduced.
- Run
  `./gradlew :public-shared-features-xcm:testDebugUnitTest --tests 'jp.co.soramitsu.xcm.SubstrateXcmTransferEngineTest' --tests 'jp.co.soramitsu.xcm.XcmServiceTest' --tests 'jp.co.soramitsu.xcm.domain.ApprovedXcmRouteRegistryTest' --tests 'jp.co.soramitsu.xcm.domain.XcmEntitiesFetcherTest'`
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
- Run `bash ./scripts/test-xcm-effective-registry-audit.sh`, then run
  `bash ./scripts/audit-xcm-effective-registry.sh --write-report build/reports/xcm-effective-registry-report.json`.
  Confirm the APK-owned `approved_xcm_routes.tsv`, the required-route manifest,
  and the bundled executable route set are exactly equal; review the attested
  SHA-256/byte lengths and the report policy showing that transaction authority
  is the APK-approved intersection and remote execution is never trusted. The
  report's `effective` count is only the compatible approved candidate set;
  `summary.productionExecutable` and every per-route `productionExecutable`
  must remain zero while the release flag is false. The audit must also confirm
  that release `CHAINS_URL` is the literal canonical production discovery URL
  with no release/generic override path and that release
  `ENABLE_PRODUCTION_XCM_TRANSFERS` is a
  hardcoded `false` with no environment or Gradle-property bypass. Debug-only
  enablement must use strict literal `true`/`false` parsing of the distinct
  `ENABLE_DEBUG_XCM_TRANSFERS` input.
- Before enabling production XCM, run
  `bash ./scripts/audit-xcm-effective-registry.sh --discovery-url https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json --require-all-approved --write-report build/reports/xcm-effective-registry-report.json`.
  Remote-only additions in `summary.extra` are discovery-only and never gain
  transaction authority. Every one of the 15 approved routes must be effective;
  use the generated report as the release-time authority instead of copying a
  mutable live-intersection count into documentation. A failure must still write
  the deterministic missing-route reasons and input digest.
  This live report attests release-time URL bytes, not the exact in-process
  runtime snapshot. Runtime discovery is narrowing/advisory only and must come
  from a successful canonical sync in the current app process; failed or
  not-yet-completed sync must expose no effective routes and must never fall
  back to the persisted Room snapshot. Keep production transfers disabled
  while `runtimeDiscoverySnapshotBoundToReport` and
  `runtimeDiscoveryFreshnessEnforced` are false. Before changing either policy,
  test fetch failure, stale-cache replay, remote route removal, rollback, and
  clock-skew cases and add signed/hashed provenance plus bounded freshness.
- Run `bash ./scripts/test-xcm-production-evidence-audit.sh` and
  `bash ./scripts/audit-xcm-production-evidence.sh` to confirm the committed
  XCM production evidence manifest is internally consistent. Before enabling
  broad production XCM, run
  `bash ./scripts/test-xcm-production-evidence-template.sh` and
  `bash ./scripts/generate-xcm-production-evidence-template.sh --output build/reports/xcm-production-evidence-template.json`
  to create the required-route evidence skeleton, update
  `scripts/xcm-production-evidence.json` to
  `status: ready`, set `releaseEnabled: true`, remove every discovery-only
  route gap, make the live effective-registry gate above pass, attach E2E
  transfer evidence for every route in both
  `runtime/src/main/assets/approved_xcm_routes.tsv` and
  `scripts/xcm-required-routes.tsv`, set each evidence record `androidCommit`
  to the Android release commit under validation, then regenerate and validate
  the canonical live report in the same fail-closed command:
  `bash ./scripts/audit-xcm-effective-registry.sh --discovery-url https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json --require-all-approved --write-report build/reports/xcm-effective-registry-report.json && bash ./scripts/audit-xcm-production-evidence.sh --effective-registry-report build/reports/xcm-effective-registry-report.json --require-ready`.
  Every route record must independently attest a finalized successful origin
  extrinsic and destination event with positive recipient balance delta, block
  hashes/numbers, distinct public HTTPS proof links, and verification time.
  Verify both sides against canonical RPCs plus an explorer, and require an
  `independentVerifier` distinct from the release `operator`. The offline audit
  validates this public attestation contract; it does not itself query the
  chains or prove that human/canonical-RPC verification was performed.
  For validating a tagged release from a different checkout, set
  `XCM_PRODUCTION_EXPECTED_COMMIT` to the intended 40-character Android commit
  when running the audit.
- Run `bash ./scripts/check-iroha-mobile-sdk-release-assets.sh --self-test`.
  If `IROHA_MOBILE_SDK_RELEASE_TAG` is configured for the release, also run
  `bash ./scripts/check-iroha-mobile-sdk-release-assets.sh --download --tag "$IROHA_MOBILE_SDK_RELEASE_TAG"`.
  Then run
  `bash ./scripts/test-iroha-production-send-readiness-audit.sh && bash ./scripts/audit-iroha-production-send-readiness.sh`.
  Artifact validation does not unblock send: keep the default unavailable
  signer and Nexus disabled while
  `config/iroha-production-send-readiness.json` is `blocked`.
  Until an authoritative fee policy is recorded, require Iroha fee estimation
  to fail with typed `AUTHORITATIVE_POLICY_UNAVAILABLE`; a displayed zero fee
  is a release-blocking regression.
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
- Keep the default `UnavailablePasskeyBackupAuthorizationProvider` in place
  until a reviewed issuer supplies one-time, exact-request-body-bound grants
  backed by release Play Integrity/signing evidence. Its authorization subject
  must represent stable Fearless wallet ownership across Android and iOS, not a
  Google account or device identifier. Confirm missing, malformed, replayed,
  wrong-body, and wrong-subject grants fail before any challenge is issued.
- Keep `UnavailableRecoverablePasskeyBackupKeyProvider` in place until product
  and security approve a wallet-owned, cross-device recovery source for an
  exact 32-byte backup key. Android Keystore keys are device-local and must not
  be presented as recovery keys. Verify loss/replacement-device recovery plus
  wrong-key, tamper, metadata-swap, truncation, and nonce-uniqueness tests for
  the canonical AES-256-GCM envelope before enabling the flag.
- Verify credential list, single revoke, and revoke-all use exact-body-bound
  grants. Deletion must durably revoke all server credentials before removing
  the Drive record; a revoke failure must leave the encrypted record intact.
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
- Confirm release CI restored `GOOGLE_SERVICES_RELEASE_JSON_B64`,
  `ANDROID_RELEASE_KEYSTORE_B64`, signing passwords, and publishable partner
  identifiers before building the release artifact. Restore
  `PLAY_SERVICE_ACCOUNT_JSON_B64` only in the conditional Play-testing step;
  it must not be job-wide. Server-side partner secrets must never be injected
  into the APK.
- Confirm `verify-android-distribution-preflight.sh --release-signing` rejects
  every missing, placeholder, control-bearing, oversized, or malformed compiled
  release integration value. This includes the NFT Alchemy key in addition to
  Ramp, Coinbase, MoonPay, X1, Google OAuth, RPC/explorer, Reown, Dwellir, and
  TON configuration; a signed but integration-empty AAB is not releasable.
- Require review and green CI before merge.
- Merge with a merge commit so the release boundary is visible.
- Create the release tag only after the merge commit is on `master`.
- Use a strict `v`-prefixed SemVer tag whose version matches the committed
  `versionName`, then dispatch `Android Release` from `master` with that exact
  tag. Confirm the exact tagged commit has a completed successful Android CI
  `push` run on `master`; a similarly named check or PR-only run is insufficient.
- For rapid link testing, build only `:app:bundleInternalAppSharing` and upload
  the resulting `app-internalAppSharing.aab` manually to Internal App Sharing.
  Treat it as debug-signed IAS-only evidence. For production-equivalent testing,
  select `internal`, `alpha`, or `beta` in the tagged workflow. An
  anyone-with-the-link Open Testing release additionally requires Play Console
  beta-track setup, declarations/review, countries, and installable status.

## After Release

- Verify the distributed artifact matches the tagged commit.
- Monitor crash, ANR, wallet creation, import, transfer, and indexer error rates.
- Keep the hotfix path ready from `master` until rollout completes.
