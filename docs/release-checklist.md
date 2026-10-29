# Release Checklist

Use this checklist for every release PR from `master` to `master`. The Android
release process in `docs/releases/PROCESS.md` remains the detailed app-specific
procedure.

## Before The Release PR

- Confirm all release work has landed on `master`.
- Confirm the release version and changelog entry are final.
- Confirm `core-db/released-database-versions.txt` has the line
  `<versionName>+<versionCode>=<APP_DATABASE_VERSION>` for this build and that
  `:core-db:testDebugUnitTest` passes `AppDatabaseVersionFloorTest` and
  `NoDestructiveMigrationSourceTest`. `APP_DATABASE_VERSION` must be at least
  the highest database version ever shipped; never lower or delete a line.
  Run `bash scripts/test-no-destructive-migration-audit.sh` and
  `bash scripts/audit-no-destructive-migration.sh HEAD` on the release commit.
- Confirm no private keys, store credentials, analytics tokens, or local
  environment files are committed.
- Confirm the MoonPay signing secret formerly embedded in released Android
  artifacts has been revoked/rotated with MoonPay. Permit only the reviewed
  publishable-key/manual-wallet hosted flow in the client. Prefilled wallet
  addresses and URL signatures require a backend-held secret and must remain
  absent from Android artifacts.
- Confirm `versioning/version.properties` contains an unused Play version code
  greater than every active, draft, and historical testing-track artifact.
  Production is 229, internal builds used 230 and Open testing holds 231, so
  4.3.0 uses 232.
- Run `bash scripts/test-android-release-signed-tag-policy.sh`,
  `bash scripts/test-android-release-governance.sh`,
  `bash scripts/test-android-play-release-guards.sh`,
  `bash scripts/test-android-release-aab-signer.sh`,
  `bash scripts/test-android-unsigned-release-build.sh`,
  `bash scripts/test-android-internal-app-sharing.sh`,
  `bash scripts/test-android-release-source-binding.sh`,
  `bash scripts/test-android-release-source-tree.sh`,
  `bash scripts/test-fearless-utils-source-integrity.sh`,
  `/bin/bash -p scripts/test-gradle-dependency-provenance.sh`,
  `bash scripts/test-android-moonpay-client-secret-policy.sh`,
  `bash scripts/verify-android-moonpay-client-secret-policy.sh`,
  `bash scripts/test-android-aab-jar-signature.sh`,
  `bash scripts/test-android-aab-identity.sh` against a freshly built AAB and
  the checksum-pinned bundletool,
  `bash scripts/test-android-aab-native-page-alignment.sh`,
  `bash scripts/test-android-release-build-log.sh`,
  `bash scripts/test-android-google-drive-web-client.sh`,
  `bash scripts/test-release-overlay-interruption.sh`,
  `bash scripts/test-android-release-media-permissions.sh`, and
  `bash scripts/verify-android-release-media-permissions.sh app/src/main/AndroidManifest.xml`.
  Verify every exact candidate AAB with
  `python3 scripts/verify-android-aab-native-page-alignment.py <exact.aab>`.
  Run `node scripts/test-google-play-internal-app-sharing-handoff-writer.js`,
  `bash scripts/test-google-play-internal-app-sharing-publication-audit.sh`,
  and `bash scripts/audit-google-play-internal-app-sharing-publication.sh --manifest-only`.
  The recorded IAS publication remains test-only historical evidence; it is
  not device-install, independent-tester, production-signing, or current-link
  availability evidence.
  Confirm the MoonPay audit scans every tracked Android module source set,
  Gradle/build input, release script, and GitHub workflow rather than only the
  former wallet provider paths, and rejects all 16 adversarial fixtures.
  Confirm the release-architecture guard reports exactly 4 positive and 136
  deterministic negative/adversarial cases. It must reject a combined
  `RELEASE_OVERLAY_MODE=release`, signing credentials exposed to Gradle, every
  Play service-account/API/Gradle publisher path, reviewer overlap between
  build and signing environments, source-tree drift, unpinned Java inputs,
  weakened downloaded provenance, and reordered attestation or cleanup.
  Confirm governance reports exactly 3 positive and 43 negative cases, and the
  source-tree gate reports exactly 3 positive and 14 adversarial cases.
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
  identity reports 2 positive and 135 adversarial cases, while its explicit R8
  policy suite reports 4 positive and 19 negative/adversarial cases. Its
  source-to-policy fixture synthesizer must also report exactly 1 positive and
  15 bounded negative/adversarial cases, including the exact source, output,
  entry-count, per-entry, and aggregate-expansion limits. Confirm
  the release build-log suite reports 3 positive and 30 adversarial cases and rejects
  cached, skipped, or warning-bearing R8 evidence and a missing or blank
  `WEB_CLIENT_ID_RELEASE`. Confirm the Google Drive web-client suite reports
  3 positive and 27 adversarial cases (see Google Drive Backup below).
  Confirm Gradle dependency provenance is strict: the wrapper distribution
  checksum, verification metadata plus its digest, the root buildscript lock,
  and the production release lockfile must all match, while CI `mavenLocal()`
  and verification/lock rewrite bypasses fail closed. Its fixture suite must
  report exactly 2 positive and 65 adversarial cases. Confirm the signed-tag
  suite reports exactly 1 positive and 13 negative cases, including combined
  repository-variable, tagger, public-key, and signer replacement.
  Confirm the AAB contract derives package/version and the embedded
  `jp.co.soramitsu.fearless.RELEASE_COMMIT` value from the bundle itself,
  rejects a source-SHA mismatch, and verifies the exact current 16-file native
  inventory and checksums. Unexpected libraries, duplicate entries, and
  substitutions of sodium, sr25519, SQLCipher, or AndroidX path binaries must
  fail.
  Do not accept Play’s broad-media “continue anyway” override: the version-code
  231 release and merged manifests must contain neither `READ_MEDIA_IMAGES` nor
  `READ_MEDIA_VIDEO` and must supersede legacy open-test builds 221/109.
- Run `bash ./scripts/test-branch-flow-audit.sh && bash ./scripts/audit-branch-flow.sh`
  and confirm the release branch flow rules still pass.
- Run
  `bash ./scripts/test-public-artifact-provenance-audit.sh && ./scripts/audit-public-artifacts.sh --strict-provenance`
  and confirm the exact nine-artifact checksum/source contract passes.
- From the final release artifact context, run
  `./scripts/audit-public-artifacts.sh --release --strict-provenance` and retain
  the exact signed artifact, source, dependency, and checksum evidence.
- Confirm GitHub serves both exact runtime source revisions from
  `config/android-runtime-source-pins.json`: Utils `3d22dad159ca5304d199acd08c5a622dea3c1577`
  and guarded WebSocket `18a94e2db0f67a8387414bc7db53aac48ded18d7`. These are historical import identities. Current code is app-owned and bound by
  the file manifest and complete app tree; review both when changing sources.
- Run `python3 scripts/test-android-runtime-source-pins.py` and
  `bash scripts/verify-android-runtime-sources.sh`; verify exact owned sources and
  original import provenance. The complete app commit/tree binds all runtime code.
- Run `python3 scripts/test-owned-remote-config.py` and
  `python3 scripts/verify-owned-remote-config.py`; publish the reviewed config and
  asset closure to public master before shipping the new consumer URLs.
- Run `bash scripts/test-public-dependency-upstream-delta-export.sh` and export the
  ownership/provenance receipt with `scripts/export-public-dependency-upstream-delta.sh --output build/reports/public-dependency-upstream-delta`.
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
  `./gradlew :core-xcm:testDebugUnitTest --tests 'jp.co.soramitsu.xcm.SubstrateXcmTransferEngineTest' --tests 'jp.co.soramitsu.xcm.XcmServiceTest' --tests 'jp.co.soramitsu.xcm.domain.ApprovedXcmRouteRegistryTest' --tests 'jp.co.soramitsu.xcm.domain.XcmEntitiesFetcherTest'`
  and confirm the public XCM transfer-engine contract remains fail-closed by
  default and rejects malformed routes before backend delegation.
- Run
  `./gradlew :runtime:testDebugUnitTest --tests 'jp.co.soramitsu.runtime.multiNetwork.chain.remote.XcmLocalChainsRegistryTest'`
  and confirm the bundled registry carries no execution spec for any route.
  Every XCM execution route was retired on 2026-10-24: relay reserve transfers
  of DOT and KSM are rejected on chain since the Asset Hub Migration, and
  Moonbeam, Moonriver and Parallel are no longer registered on their relay
  chains. A route may return only with a reserve-location decision for its
  exact origin and destination, a successful `DryRunApi` result on the live
  origin runtime, an engine test that encodes and decodes the call against that
  runtime's metadata, and funded two-chain evidence.
- Run `bash ./scripts/test-xcm-registry-metadata-audit.sh` and then
  `bash ./scripts/audit-xcm-registry-metadata.sh --write-gap-report build/reports/xcm-registry-gap-report.json --require-route-file scripts/xcm-required-routes.tsv --require-gap-file scripts/xcm-discovery-only-routes.tsv`
  and confirm every committed XCM execution spec (currently none) is valid.
  Review `build/reports/xcm-registry-gap-report.json` and
  `scripts/xcm-discovery-only-routes.tsv`; confirm every `missingExecutionSpec`
  route is intentionally still discovery-only and categorized as `bridge-sora`,
  `non-native-asset`, or `cross-parachain-native`. Before enabling a
  non-default public XCM transfer engine for all advertised routes, also run
  `bash ./scripts/audit-xcm-registry-metadata.sh --require-executable --require-all-routes-executable`
  and confirm the registry contains real executable metadata for every
  advertised XCM route. Both flags fail while the registry has no executable
  route, which is the intended state until a route is requalified.
- Run `./gradlew :runtime:testDebugUnitTest --tests 'jp.co.soramitsu.runtime.multiNetwork.chain.remote.BundledMutationRouteManifestTest'`
  and confirm `common/src/main/assets/mutation_route_manifest.json` carries the
  current SHA-256 of `approved_xcm_routes.tsv` and `local_chains.json`; editing
  either file without refreshing the manifest denies every new XCM mutation.
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
  `bash ./scripts/audit-xcm-effective-registry.sh --discovery-url https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/production/chains/v13/chains.json --require-all-approved --write-report build/reports/xcm-effective-registry-report.json`.
  Remote-only additions in `summary.extra` are discovery-only and never gain
  transaction authority. Every approved route (none while all routes are retired) must be effective;
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
  `bash ./scripts/audit-xcm-effective-registry.sh --discovery-url https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/production/chains/v13/chains.json --require-all-approved --write-report build/reports/xcm-effective-registry-report.json && bash ./scripts/audit-xcm-production-evidence.sh --effective-registry-report build/reports/xcm-effective-registry-report.json --require-ready`.
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
  Ready evidence expires: `lastReviewed` and every transfer `timestamp` must be
  at most 30 days old when the audit runs, so evidence gathered before a runtime
  upgrade, a migration or a chain sunset cannot qualify a later release.
  `XCM_PRODUCTION_EVIDENCE_NOW` exists only for the audit's own tests; never set
  it in CI. The release flag is bound to this manifest: configuring
  `feature-wallet-impl` fails when release `ENABLE_PRODUCTION_XCM_TRANSFERS` is
  anything but `false` while the manifest is not `ready` and release-enabled,
  and both CI and the release workflow run the live `--require-ready` audit
  whenever `scripts/xcm-release-submissions-enabled.sh` reports the flag on.
- Run `bash ./scripts/check-iroha-mobile-sdk-release-assets.sh --self-test`.
  If `IROHA_MOBILE_SDK_RELEASE_TAG` is configured for the release, also run
  `bash ./scripts/check-iroha-mobile-sdk-release-assets.sh --download --tag "$IROHA_MOBILE_SDK_RELEASE_TAG"`.
  Then run
  `bash ./scripts/test-iroha-production-send-readiness-audit.sh && bash ./scripts/audit-iroha-production-send-readiness.sh`.
  Artifact validation does not unblock send: keep the default unavailable
  signer and Nexus disabled while
  `config/iroha-production-send-readiness.json` is `blocked`.
- Remote feature switches are veto-only (`docs/remote-feature-switches.md`).
  Confirm the production file still matches the committed copy, then run the
  continuity tests:
  `curl -fsS https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/production/appConfigs/feature_toggle | cmp - common/src/test/resources/remote-config/feature_toggle.production.json`
  and
  `./gradlew :common:testDebugUnitTest --tests '*FeatureContinuityTest*' --tests '*ProductFeatureToggleStoreTest*'`.
  With no capability keys published, Polkaswap swaps and liquidity, Demeter
  farming and Bitcoin/Solana sends stay on for every upgrader. Never publish a
  veto preemptively. On the release build, smoke-test a small funded SORA swap,
  adding and removing liquidity, and a Demeter deposit and withdrawal.
- Solana uses public JSON-RPC nodes unless the optional repository secret
  `FL_ANDROID_SOLANA_RPC_URL` names an `https` mainnet provider for the build
  (asked first; the key goes in the URL path or query, and a malformed value
  fails the build). Without one, token balances come from
  api.mainnet-beta.solana.com or, when it is busy, public.rpc.solanavibestation.com
  (publicnode refuses token-account reads).
  Run a small funded SOL and SPL canary on the release build; see
  `docs/solana-send.md`.
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
- The disabled Drive adapter binds each list/upload/download/delete sequence to
  one selected Google account and access token, follows bounded list pages, and
  rejects duplicate, incomplete, or mismatched file results. Its current
  five-property metadata format rejects values above Drive's 124-byte
  key-plus-value limit. A reviewed portable generation format is still needed
  for long metadata, conditional immutable uploads, download/decryption
  verification, and retention of the last decryptable generation.
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
- The disabled client-side WebAuthn PRF wrapper derives a 32-byte AES-GCM
  wrapping key from a 32-byte native PRF result with HKDF-SHA256. Its versioned
  context binds owner, credential, wallet, backup-envelope metadata, and key
  epoch; the 32-byte backup key is never given to the authority service. The
  fixed cross-platform vector and tamper tests exercise this primitive only.
  Registration can request evaluation of a public 32-byte PRF salt; if the
  provider omits the result, a fresh server-verified assertion for that exact
  credential must evaluate its stored salt. The discoverable authentication
  assertion remains separate. These request builders do not establish provider
  support or authorize recovery, and local PRF output must never enter a server
  credential request, log, or crash report.
  Keep the recoverable key provider unavailable until credential ceremonies,
  wrapper storage in verified immutable Drive generations, key rotation,
  replacement-device tests, and independent review are complete.
- The challenge service rejects final-credential removal without an explicit
  confirmation in the grant-bound request body. Android sends that field only
  while compensating a registration whose backup has not completed verification.
  User-requested backup deletion remains blocked when live credentials exist;
  before enabling it, add explicit confirmation, surviving-wrapper/key rotation,
  and coordinated revocation in both credential services. Do not treat the
  server's count of credentials as proof of a decryptable recovery route.
- The MoonPay client-secret audit still scans every tracked shipping input. Its
  necessary `HmacSHA256` exception is limited to the exact SHA-256-pinned
  passkey HKDF source; changes to that file fail the audit until the pin is
  deliberately updated after review. No MoonPay signing key or helper is
  allowed in the app.
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
- Confirm Android CI preserves the exact migration-evidence order: verify the
  instrumentation results, prepare portable evidence with
  `python3 ./scripts/prepare-android-migration-evidence.py`, then upload. The
  upload must use
  `${{ always() && steps.prepare_migration_evidence.outcome == 'success' }}` and
  consume only `build/reports/android-migration-upload/**`; never add raw AGP
  result paths to that upload.
- Confirm Universal Wallet migrations, legacy export-only access, and supported
  network registry changes are documented in the PR.
- Confirm rollback owner, monitoring owner, and release communication channel.

## Google Drive Backup

Upgrading users keep encrypted wallet backups in Google Drive. Drive stores
them in the `appDataFolder` of the Google Cloud project that owns the app's
OAuth clients, project `621183184145` (`manticora-37173`). A release that signs
users in through another project, or cannot sign in at all, makes those
backups look gone.

- **Secret.** `WEB_CLIENT_ID_RELEASE`, an `android-release-build` environment
  secret consumed only by "Build exact unsigned release AAB" (put it in
  `local.properties` for a local release build). Gradle compiles it into
  `BuildConfig.WEB_CLIENT_ID`, which `AccountFeatureModule` passes to
  `BackupService.create`. Drive does not read `google-services.json`, so the
  Firebase overlay does not configure it.
- **Value.** The web client compiled into every official release from 2.4.1
  through 4.2.0: `621183184145-<32 characters>.apps.googleusercontent.com`,
  whose SHA-256 is pinned in
  `.github/release/android-google-drive-web-client-sha256.txt`. It is **not**
  the `client_type: 3` client in 4.2.0's `google-services.json`, which is a
  different client. (2.3.1 compiled the Android client of the same project, so
  it used the same Drive folder.) To check a value without printing it, run
  `python3 scripts/verify-android-google-drive-web-client.py <artifact>`
  against the official `v4.2.0` GitHub APK (SHA-256
  `3d4a9058f31ca6697275435bbaf788528d8ac37c4043f8ec18c12e77503f6bf7`, client
  in `classes2.dex`) and against the candidate AAB or APK. The script prints
  only the project number and the digest.
- **Automatic gates.** `scripts/verify-android-release-build-log.sh` fails
  when Gradle warns that `WEB_CLIENT_ID_RELEASE` is not defined or is blank.
  `scripts/secrets.gradle` warns on blank values because GitHub passes an
  unset secret as an empty variable. `scripts/verify-android-google-drive-web-client.py`
  then checks the unsigned and the signed AAB. Their dex files must contain
  exactly one Google OAuth client id, and it must match the pin. A changed pin
  needs a reviewed pull request that moves Drive backup to another client in
  the same Cloud project.
- A build without the client does not crash. `BackupService.isAvailable` is
  false, Welcome explains this and opens phrase or JSON import, and the backup
  screens hide the Google option. This fallback is not an acceptable Play
  release state.
- **Signing certificates.** Google grants Drive access only to an Android
  OAuth client registered in project `621183184145` for the package and the
  SHA-1 of the signing certificate. 4.2.0's `google-services.json` registers
  `jp.co.soramitsu.fearless` with SHA-1
  `B6:EE:8D:19:CB:CE:5F:03:0A:14:67:29:3D:A6:43:58:A7:7F:88:55`. That is not the
  upload certificate (SHA-1
  `EA:92:30:9B:DE:37:1A:AB:43:0D:1B:E5:E8:F5:3C:8E:AE:FF:36:EC`), which signs
  the workflow AAB and the 4.2.0 GitHub release APK, so it should be the Play
  app-signing certificate. Confirm this under Play Console, Test and release,
  App integrity, App signing. A build signed with any unregistered certificate
  gets `DEVELOPER_ERROR` (status 10) from Google sign-in and shows "Google
  Drive backup isn't available".
  - Qualify Drive on a build installed from a Play testing track (internal
    testing is enough), which Play signs with the app-signing key.
  - Sideloaded upload-key builds, GitHub release APKs and IAS builds cannot
    qualify Drive. IAS builds also ship the public placeholder and no web
    client. To test such a build, register its certificate SHA-1 as an
    additional Android OAuth client in project `621183184145`; that is a
    Google Cloud console action for the team.
  - A debug build (`jp.co.soramitsu.fearless.debug`) works when the SHA-1 of
    its debug keystore is one of the five registered for that package
    (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`).
    It needs no `WEB_CLIENT_ID_DEBUG`, because debuggable builds stay available
    and request no ID token when the id is blank. It reads and writes the real
    Drive folder of the signed-in account, so use a test Google account.

### Manual Drive Acceptance Protocol

Run this on a Play testing-track candidate before widening the rollout, and
record the results in the release PR. Use a test Google account and a
disposable device with no funds. Write down every recovery phrase. Never
delete a local wallet or a Drive backup whose recovery phrase is not written
down.

1. Install Play 4.2.0 (version code 229). Create or import these wallets and
   back each up to Google Drive with its own backup password:
   - sr25519 mnemonic, no derivation path
   - sr25519 mnemonic with `//polkadot/0`
   - ed25519 mnemonic
   - ECDSA (Ethereum) raw-seed import
   - JSON import

   Record each wallet's Substrate and Ethereum addresses.
2. In Chrome extension 3.0.5, signed in to the same Google account, back up one
   Substrate+Ethereum wallet and one Substrate-only wallet to Google Drive, and
   record their addresses.
3. Upgrade the device in place to the candidate through the testing track; do
   not uninstall. The account stays signed in. "Import from Google" lists
   every app and extension backup exactly once, and the two extension backups
   say "Backed up from the browser extension" in the list and on the password
   screen. On a second device, import each backup and confirm the addresses
   match steps 1 and 2.
4. Back up a new wallet from the candidate. Import it with Play 4.2.0 on
   another device and confirm the addresses match (older apps can read new
   backups).
5. Check the error paths:
   - A wrong backup password shows "Incorrect backup password" and allows
     another try.
   - Airplane mode shows "Couldn't reach Google Drive" with Try again.
   - Removing Fearless's access under myaccount.google.com, Security,
     third-party connections, then opening the backup list, shows Google's
     consent screen, and the list loads after you allow access.
   - Cancelling the Google account picker leaves "Backup to Google" available
     to try again.
   - "Import from Google" with a Google account that holds no Fearless
     backups shows "No backups in this Google account". "Use another Google
     account" opens the account picker and lists the test account's backups,
     and "Import another way" opens recovery phrase import.

GoogleSignIn is deprecated but still works in play-services-auth 21.6.0.
Moving to `Identity.getAuthorizationClient` is planned for a later release
(`docs/roadmap.md`).

## Internal App Sharing Smoke Evidence

Use this section when an IAS tester link is requested. IAS evidence is
supplemental and never replaces any production release check above.
IAS handoff status remains **pending** until every frozen suite, fresh-build
check, verifier check, and trust-boundary check below is green in the eligible
manual workflow run.

> [!WARNING]
> The IAS file is debug-signed and Google re-signs it. It cannot upgrade a
> Play-installed Fearless Wallet. Never uninstall a funded wallet to install
> IAS. Never claim IAS validates production database migration or production
> release signing. The public placeholder also cannot qualify live Firebase,
> Google OAuth/sign-in, or Google Drive/passkey backup and restore.

- Treat every pull-request workflow run as non-distributable validation only.
  It must create no handoff and upload no artifact. The only eligible handoff
  is from `workflow_dispatch` in `soramitsu/fearless-Android` at exactly
  `refs/heads/master`, after the change is merged, while `master` is protected
  and the candidate commit still equals the fetched remote `master` head.
- Confirm `versioning/version.properties` still declares `4.2.0` / `231`.
  The workflow handoff filename and verifier pin `4.2.0-ias` / `231`; whenever
  either source-controlled version changes, update and review all of those
  hardcodes in the same commit. Never rename an older AAB as a newer version.

- Confirm CI starts from a clean checkout and `RELEASE_COMMIT` is exactly the
  lowercase 40-character `HEAD`. Confirm the expected
  `FEARLESS_UTILS_COMMIT` and `FEARLESS_UTILS_EFFECTIVE_TREE` match the verified
  utils checkout.
- Confirm the workflow order is: repository/event/ref gate; exact app and utils
  checkouts; source binding; Java, pinned bundletool, Android SDK/NDK and Rust
  setup; static and full IAS Gradle suites; old-output removal and source check;
  exact fresh unsigned bundle; unsigned verification; and, only for an eligible
  manual run, a one-day untrusted producer quarantine. A fresh qualifier must
  download that exact artifact by ID and digest, validate it under a disposable
  user, kill that user, sign externally with a step-local JKS, erase the key,
  verify with only the public certificate under another disposable user, create
  the handoff, upload it with a pending name, download back and revalidate the
  exact bounded archive under a third disposable user, then record completion.
  The separate finalizer retains only a fully qualified current artifact and
  deletes every unqualified one; the scheduled janitor removes stale pending
  artifacts.
- Run `bash scripts/test-android-internal-app-sharing.sh` and require exactly 8
  positive, 39 behavioral negative, and 645 static adversarial assertions.
  Its bounded Gradle coverage-cleanup prerequisite must independently report
  exactly 4 positive and 12 negative/adversarial cases.
  Require the dependency-provenance suite to report exactly 2 positive and 65
  adversarial cases. The workflow's Linux public-certificate AAB suite must
  report 7 positive and 78 adversarial artifacts. Local AAB expectations are
  7/76 for macOS certificate mode, 7/84 for macOS keystore mode, and 7/86 for
  Linux keystore mode. A changed total requires review.
- Confirm the artifact build requested exactly
  `:app:bundleInternalAppSharing`, with `CI=true`, and no additional Gradle
  task, and included `--no-build-cache --rerun-tasks`. Do not accept `assemble`,
  an abbreviated/unqualified task, a publish task,
  `ANDROID_UNSIGNED_RELEASE_BUILD`, injected signing properties, any release
  keystore/password, Play credential/control, or production Firebase overlay
  input.
- Confirm pull-request validation received no WalletConnect project ID. For an
  eligible protected-`master` manual dispatch, require the exact build step to
  receive a non-logged 32-hex `secrets.FL_WALLET_CONNECT_PROJECT_ID`; no other
  step may reference it. Reject the handoff if a valid `wc:` pairing scan is
  not exercised during the trusted device soak.
- Confirm the variant retained release minification/resource shrinking,
  package ID, version code, source-bound manifest, component/permission
  surface, and native payloads. Confirm Gradle emitted an unsigned quarantine
  AAB whose only application-identity difference is the `-ias` version-name
  suffix. Confirm the fresh qualifier's run-bound JKS had mode `0600`, Android
  Debug identity, a bound uppercase certificate SHA-256 distinct from the
  production upload certificate, and 30-day validity; it must be erased before
  public-certificate-only verification. Confirm the production `release`
  signing configuration was unchanged.
- Confirm `processInternalAppSharingGoogleServices` had exactly one finalized
  typed JSON input: the checksum-pinned reviewed `fearless-public` file at
  `app/src/release/google-services.json`. No bytes may be copied into a variant
  source set, and `app/src/internalAppSharing` must remain absent before, during,
  and after success or forced failure. Supported IAS smoke scope is limited to
  install/fresh launch, cold start, onboarding/navigation, local-only flows with
  disposable test material, and screens independent of the disabled services.
  Unsupported scope includes live Firebase, Google OAuth/sign-in, Drive/passkey
  backup or restore, Play upgrade, production migration, and production signing.
- Treat `scripts/verify-android-internal-app-sharing-aab.sh` as workflow-only,
  under `CI=true` and the source-bound `RELEASE_COMMIT`. It requires the
  checksum-pinned `BUNDLETOOL_JAR` and exact `FEARLESS_UTILS_COMMIT`,
  `FEARLESS_UTILS_EFFECTIVE_TREE` import-provenance fields. Unsigned mode
  forbids signer inputs. Signed and `--signed-from` modes additionally require
  the exact uppercase `EXPECTED_IAS_DEBUG_CERT_SHA256` and exactly one of the
  private `ANDROID_IAS_DEBUG_KEYSTORE_PATH` or public-only
  `ANDROID_IAS_DEBUG_CERTIFICATE_PATH`. Pass the relevant unsigned/signed AAB
  inputs and expected commit, plus a new absolute private output path only when
  creating the next boundary artifact. Retain its AAB SHA-256 and output
  covering signer/JAR
  signature, embedded commit, identity, SDK, components/permissions, native
  libraries, and compiled public Firebase resources.
- Run `bash scripts/test-android-internal-app-sharing-aab.sh` against the fresh
  unsigned/signed pair in public-certificate mode and require exactly 7
  positive and 78 adversarial artifacts on the workflow's Linux runner.
  Confirm tampering, an invalid signing transform, wrong-signer bytes, source
  mismatch, invalid identity, resource exhaustion, publication interruption,
  and malformed tooling all fail closed.
- Re-run the exact source-tree check after the build. Confirm neither CI nor
  Gradle invoked a publisher, consumed a Play service account, uploaded to
  Google/Play, or mutated a Play track.
- Confirm the eligible manual `android-internal-app-sharing.yml` run retained
  only the verified GitHub Actions handoff artifact and its integrity evidence,
  with seven-day retention, after exact artifact-ID/digest download-back and a
  successful separate finalizer. Have an authorized operator download that
  handoff and manually upload only its exact verified AAB on Play Console's
  **Internal App Sharing** page. Record the source commit, workflow run,
  trusted-master eligibility marker, artifact ID/digest, AAB SHA-256,
  run-bound signer fingerprint, IAS upload identity, and Google-generated
  tester URL. Do not use PR-run output, a failed/unfinalized pending artifact,
  or put the debug-signed artifact on a Play testing or production track.
- Test IAS only on an unfunded disposable device/profile with no
  Play-installed Fearless Wallet. For real migration qualification, retain a
  disposable Play-installed wallet and upgrade it through the intended Play
  testing track with a newer Play-signed candidate; do not use IAS evidence.

## Release PR To `master`

- Open the PR from `master` or `release/<version>` to `master`.
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
  `AD:6C:1D:07:BE:5D:FA:8C:53:9E:8C:9B:8B:D4:3D:C1:C6:CF:79:A8:39:82:8B:10:B6:F9:53:AD:57:C1:BD:E8`.
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
- Keep the hotfix path ready from the shipped release tag until rollout
  completes; follow `docs/rollback-checklist.md`, never a `master` or
  `master` base that lacks the tag.
