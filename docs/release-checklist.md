# Release Checklist

Use this checklist for every release PR from `develop` to `master`. The Android
release process in `docs/releases/PROCESS.md` remains the detailed app-specific
procedure.

## Before The Release PR

- Confirm all release work has landed on `develop`.
- Confirm the release version and changelog entry are final.
- Confirm no private keys, store credentials, analytics tokens, or local
  environment files are committed.
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
- For release artifact hardening, restore private overlays and run
  `./scripts/audit-public-artifacts.sh --release --strict-provenance`; the
  Android Release workflow runs the same strict provenance gate before building
  the release artifact.
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
  `scripts/xcm-required-routes.tsv`, and run
  `bash ./scripts/audit-xcm-production-evidence.sh --require-ready`.
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
- Confirm release CI restored `GOOGLE_SERVICES_RELEASE_JSON_B64`,
  `ANDROID_RELEASE_KEYSTORE_B64`, `PLAY_SERVICE_ACCOUNT_JSON_B64`, signing
  passwords, and partner keys before building the release artifact.
- Require review and green CI before merge.
- Merge with a merge commit so the release boundary is visible.
- Create the release tag only after the merge commit is on `master`.

## After Release

- Verify the distributed artifact matches the tagged commit.
- Monitor crash, ANR, wallet creation, import, transfer, and indexer error rates.
- Keep the hotfix path ready from `master` until rollout completes.
