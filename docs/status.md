# Status Summary

Last updated: 2026-07-30

This snapshot summarizes the current health, feature coverage, and key risks of the Fearless Wallet Android codebase.

## Overview
- Platforms: Android (Kotlin 2.1, Java 21 target). Compose enabled in selective screens.
- Ecosystems: Substrate/Polkadot, EVM (Ethereum-compatible), TON.
- Architecture: Modular feature pairs (`-api`/`-impl`), shared foundations (`common`, `core-api`, `core-db`, `runtime`). Hilt for DI.
- Build types: `debug`, `release`, `staging`, `develop`, `pr`.

## Feature Coverage (High Level)
- Wallet: Send/Receive/History/Manage Assets — present and integrated (`feature-wallet-*`).
- Accounts & Onboarding: Present (`feature-account-*`, `feature-onboarding-*`).
- Staking & Crowdloans: Present for Substrate ecosystems (`feature-staking-*`, `feature-crowdloan-*`).
- Swaps & Pools: Polkaswap and liquidity pools present (`feature-polkaswap-*`, `feature-liquiditypools-*`).
- WalletConnect v2: Initialized in `App.setupWalletConnect()` with Reown SDK.
- TON Connect: Present (`feature-tonconnect-*`).
- NFTs: Present; details screen has TODO placeholders.

## Build & CI
- CI Pipeline: `.github/workflows/android-ci.yml` runs detekt, unit tests (`runTest`), and app lint on push/PR.
- Signed Android release artifact pipeline:
  `.github/workflows/android-release.yml` builds
  only an immutable tagged `master` source after exact-head CI passes. CI has
  no Play track/status inputs and performs no Play mutation.
  Credential-free `release-controls` validates the signed tag, prior CI,
  disjoint environment governance, all adversarial release suites, and the
  exact source commit/tree before either protected stage.
  `release-build`, protected by `android-release-build`, restores only
  Firebase, runs the exact CI-only/source-bound unsigned Gradle bundle, and
  stages exactly the unsigned AAB, checksum, provenance, and bounded verified
  Gradle/R8 build log. It individually attests and verifies all four files
  before uploading the artifact.
  `release-signing`, protected by the separately reviewed
  `android-release-signing` environment, runs on a fresh runner. It downloads
  exactly those four files and revalidates the digest, provenance, source
  attestation, tag, and current `master` before restoring the upload key.
  The standalone signer preserves every non-signature entry byte-for-byte and
  atomically writes the certificate-pinned AAB. The job verifies signature,
  package/version/source/native identity, and exact merged media permissions
  from the signed bytes, records the identity/permission evidence and digest in
  final provenance, then individually attests the final AAB, checksum, and
  provenance.
  The release AAB embeds the exact tagged commit and the verifier derives it
  from the manifest, checks the exact complete 16-library native inventory
  (including SQLCipher and AndroidX), and rejects missing, duplicate, extra, or
  substituted native payloads. Provenance also binds the pinned
  `fearless-utils` commit and library-only patch checksum.
  The unsigned transfer contains exactly four entries; the final signed
  transfer contains exactly three. Both require exact checksum/provenance key
  sets. Every job repeatedly rejects tracked, staged,
  untracked, and source-like ignored drift from the approved source tree. The
  protected runners use the independently checksum-pinned Temurin
  `21.0.12+8` archive and bind its archive plus Java-tool binary digests into
  provenance. CI never receives a Play service-account credential. An
  authorized operator reviews the final evidence and uploads the AAB manually
  to the existing Open Testing track in Play Console.
- Gradle dependency provenance is fail-closed for CI/release graphs: the Gradle
  9.0 distribution SHA-256, strict dependency verification metadata and its
  tracked digest, the strict root buildscript lockfile, and the production
  release lockfile are pinned. CI/release builds reject `mavenLocal()`,
  verification-off flags, and metadata/lock rewrites.
- Android versioning is source-controlled and immutable during builds. Google
  Play production is version code 229; version code 230 is reserved for the
  migration-safe 4.2.0 testing candidate.
- Release signing has no debug-key fallback. The guarded workflow deliberately
  produces an unsigned intermediate without any keystore input, then the
  standalone signer fails if a signing input is absent, the certificate is
  invalid, or its SHA-256 is not the registered Google Play upload certificate
  `40:39:10:92:F5:B9:7E:78:2C:65:28:CC:57:1A:DF:5D:BE:DF:E2:D0:50:23:BA:BC:7C:4E:33:9E:58:4A:4A:9A`.
- The release source and every merged release manifest are gated against
  `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and broad legacy external-storage
  permissions. The legacy open-test artifacts (version codes 221 and 109)
  request broad media access; Play must be given the clean version-code-230
  bundle with the track resume instead of using its “continue anyway” override.
- MoonPay is fail-closed and absent from the buy-provider registry. Its former
  client-side HMAC secret and BuildConfig fields have been removed; re-enabling
  it requires backend signing or a supported public mobile flow. The secret
  exposed by already released Android artifacts must be revoked/rotated with
  MoonPay before the next release. The fail-closed audit now covers all tracked
  Android module source sets, build inputs, release scripts, and workflows,
  with alternate-module/buildSrc/workflow adversarial fixtures.
- Release Firebase backup construction is interruption-safe: it validates a
  restricted temporary copy's size, SHA-256, and exact mode before an atomic
  rename. Cleanup deletes incomplete temporaries. The split-overlay suite
  covers 2 independent positives, 20 phase/credential-isolation negatives, 20
  restore and 8 cleanup TERM/SIGKILL cases, and 2 backup-integrity negatives;
  the checked-in placeholder survives every covered boundary and no
  signing/Play credential remains.
- Secrets in CI: Stubbed keys for EVM and history providers keep resolution stable; real keys required locally.
- Local validation: `scripts/validate-local.sh` runs the same checks and now installs Android platform/build-tools 36 to match the compile SDK, along with NDK r28 and platform-tools.
- WalletConnect/Reown SDK: BOM 1.6.9 resolves Kotlin 2.2 runtime metadata, so
  release builds use exact AGP 8.10.1 / R8 8.10.24 with compileSdk 36; the
  bundled UniFFI native libraries retain 16 KB page alignment.
- WalletConnect Pay: dependency excluded (until Reown publishes 16 KB-native builds) to avoid packaging the `yttrium-wcpay` 4 KB libraries.
- Google Play 16 KB page-size compliance: Native bundles rebuilt with NDK r28, `readelf -l` verification runs in CI on sr25519/toolChecker libraries, and the Play Console warning is cleared.
- Native crypto rebuild tooling: `scripts/build-libsodium.sh` now auto-detects the host-specific `toolchains/llvm/prebuilt` directory (darwin/linux/windows) so libsodium can be rebuilt on non-macOS hosts without manual tweaks.
- Utils composite build: public CI checks out `soramitsu/fearless-utils-Android` at `7500809f33243ee47ecb2ec8563fc284ac4de0d6`, verifies it with `scripts/ensure-fearless-utils.sh`, and forces the local composite include. `settings.gradle` keeps Gradle 9 shims for the upstream build until that repo upgrades.
- External Play blockers: release/upload credentials are not stored locally;
  the required Firebase/upload-key GitHub Actions secrets must be configured.
  `android-release-build` and `android-release-signing` must each prevent
  self-review/admin bypass, use protected branches, and have exact, sorted,
  nonempty User-only reviewer allowlists with no reviewer ID shared between
  them. This creates two distinct environment approvals; two or more Users per
  list is optional availability redundancy. The release tagger also needs a
  locally verified OpenSSH Ed25519 signing key pinned by the exact fingerprint
  in `.github/release/android-tag-signer-fingerprints.txt`; the matching public
  key must be set in `ANDROID_RELEASE_TAG_SIGNER_PUBLIC_KEY_B64`. A mutable
  tagger-email variable is not sufficient. The exact
  version-code-230 source must merge and be tagged as
  `v4.2.0+android.230`. An authorized Play operator must then review Publishing
  overview, upload the final attested AAB to the existing beta/Open Testing
  release without changing countries or testers, submit it as installable, and
  smoke-test the public opt-in link after Google processes the update.

## Migration-Safe 4.2.0 Candidate

- `versionName=4.2.0` / `versionCode=230` is the first migration-safe testing
  candidate. Its initial destination is the existing `beta`/Open Testing track;
  Production promotion is a separate reviewed decision.
- Room accepts only the complete adjacent migration chain from database version
  9 through 77. Missing, duplicate, non-adjacent, or out-of-range edges fail
  before the user database opens, and no destructive migration fallback is
  configured.
- Released-schema and SQL preflights validate table/index/foreign-key shape,
  bounded row/cursor sizes, whole-database integrity, and wallet public/secret
  bindings before mutations. Unsafe input aborts startup instead of partially
  upgrading or wiping data.
- Legacy wallet rows that cannot be represented safely are retained in a
  recovery ledger. Unreadable or mismatched encrypted values are moved only by
  exact-ciphertext, durable quarantine operations; recovery state blocks PIN,
  export, signing, and ordinary wallet use rather than deleting evidence.
- Android Keystore-backed master-key attestation runs before the main wallet
  opens. Retryable secure-storage failure, permanent key loss, database-open
  failure, and process-restart-required state are classified separately. A
  latched storage failure invalidates the process-wide ready state.
- Account create/add-EVM/delete and TON Connect changes use bounded durable
  cross-store journals. Startup serially reconciles TON and account journals,
  inventories active/quarantined orphan namespaces, and reaches the heavy root
  activity only after database, storage, and recovery checks are coherent.
- The process-owned startup session serializes checks across recreation and
  competing launcher/deep-link intents, retains the newest ready intent, and
  prevents an Activity lifecycle cancellation from abandoning the shared
  startup result.
- Current local qualification evidence includes 517 root JVM tests (14 skipped
  by assumptions), 310 included-utils JVM tests (10 skipped), 27/27 focused
  startup contracts, and 99/99 connected tests (38 app, 53 database, 4 common,
  4 account). The production-app fingerprint remained unchanged during the
  dedicated emulator run. A fresh source-bound release build and all artifact
  verifiers must still be repeated from the final merged/tagged commit; a
  locally built or pre-commit AAB is not an upload candidate.

## Runtime & Chains
- Default types/chains under `runtime/src/main/assets`. Override via `TYPES_URL_OVERRIDE`, `DEFAULT_V13_TYPES_URL_OVERRIDE`, `CHAINS_URL_OVERRIDE` in `local.properties`.
- ChainRegistry coordinates runtime providers and connections. EVM handled via `EthereumEnvironmentConfigurator` and `EthereumConnectionPool`.

## Polkadot SDK Alignment
- Target: polkadot-stable2503 (prepared via override keys).
- How to align: set `TYPES_URL_OVERRIDE`, `DEFAULT_V13_TYPES_URL_OVERRIDE`, and `CHAINS_URL_OVERRIDE` to registries validated against stable2503. See `docs/samples/local.properties.stable2503`.
- Optional: pin `shared_features` via `SHARED_FEATURES_VERSION_OVERRIDE=1.x.y` if required by the SDK combo.
- Utils integration: the build uses a pinned `soramitsu/fearless-utils-Android` checkout as a composite source dependency.
- Debug: run `./gradlew printPolkadotSdkAlignment` to verify effective overrides.

## Health & Risks (Snapshot)
- Security hardening delivered on 2026-03-05: TonConnect manifest origin validation, TonAPI header/logging protections, WebView mixed-content restrictions, AES-GCM encrypted preferences migration path, PIN lockout backoff, and internal-cache JSON export cleanup.
- TonConnect URL controls were tightened further: `tonapi.fetch` now enforces HTTPS + `tonapi.io` host allowlist with default TLS port, and TON API auth header host matching now correctly handles `*.tonapi.io` subdomains.
- TonConnect URL validator now rejects IP-like numeric host aliases (for example `2130706433`, `127.1`, `*.localhost`) to close localhost/private-network bypasses.
- TonConnect in-app browser navigation now enforces strict same-origin policy (scheme + host + port) for loaded dApps instead of host-only checks.
- TonConnect `tonapi.fetch` bridge is now backed by the hardened TON API HTTP client and limited to `GET` requests, executed on IO dispatcher.
- Encrypted preferences key recovery now regenerates the wrapped AES key if Android keystore unwrap returns invalid bytes, avoiding crash loops on corrupted key material.
- TON data path now uses `ton-indexer` first for account state, balances, seqno/public key, and account transaction history, with automatic fallback to TonAPI on indexer errors.
- TonAPI fallback for TON data reads is now pinned to `https://tonapi.io` (including cases where chain node configuration does not include a TonAPI URL), ensuring indexer-first + TonAPI-only fallback behavior.
- TON transfer send/fee/time flows now use ton-indexer JSON-RPC (`/jsonRPC`: `sendBoc`, `estimateFee`, `getAddressInformation`) as primary path, with TonAPI retained strictly as fallback.
- TonConnect jetton transfer payload lookup now uses ton-indexer first (`/api/indexer/v1/jettons/{jetton}/transfer/{owner}/payload`) and falls back to TonAPI only when indexer is unavailable.
- TON history cursor handling now supports `lt:hash` when indexer transaction IDs are available, enabling cursor-based indexer pagination before page-scan fallback.
- TON indexer fallback now preserves coroutine cancellation semantics (`CancellationException` is rethrown), preventing cancellation bypass during indexer outages.
- Code quality: Detekt enforced in CI. Several TODO/FIXME markers remain in features and common utils.
- Incomplete UI/logic areas:
  - NFT details screen placeholders.
  - Staking validator oversubscription/slashed logic marked FIXME.
  - Substrate balance loader contains a hardcoded `chainAssetId` fallback.
  - Meta-account/EVM nullability handling flagged in multiple call sites (`accountId(chain)!!`).
  - Error text TODOs in `FearlessException` (needs resource-based messages).
  - Potentially unused network executor (`SocketSingleRequestExecutor`) flagged for deletion.

## Notable TODO/FIXME References
- Account:
  - `feature-account-api/.../AddressDisplayUseCase.kt` — adopt meta-account logic.
  - `feature-account-api/.../domain/model/Account.kt` — `cryptoType` optionality.
- Wallet & Balance:
  - `feature-wallet-impl/.../SubstrateBalanceLoader.kt` — avoid hardcoded `chainAssetId`.
- Staking:
  - `feature-staking-impl/.../Validator.kt` — oversubscribed/slashed logic.
  - `feature-staking-impl/.../StakingRelayChainScenarioInteractor.kt` — EVM nullability.
- Crowdloan:
  - `feature-crowdloan-impl/.../KaruraContributeInteractor.kt` — TODO marker.
- NFTs:
  - `feature-nft-impl/.../DetailsScreen.kt` — TODO placeholder.
- Common:
  - `common/.../FearlessException.kt` — resource texts for common errors.
  - `common/.../PreferencesImpl.kt` — listener GC TODO note.
  - `common/.../SocketSingleRequestExecutor.kt` — unused? consider removal.
- Misc:
  - `feature-account-impl/.../OptionsSwitchNodeContent.kt` — temporarily hidden button.
  - `feature-staking-impl/.../ExtrinsicBuilderExt.kt` — rename `createPool` with runtime 9390.

## Getting Started & Verifications
- Build app: `./gradlew :app:assembleDebug`
- Local checks: `bash scripts/validate-local.sh`
- Secrets: set EVM provider keys and history provider keys in `local.properties` or env vars (see README).
- WalletConnect: ensure `WALLET_CONNECT_PROJECT_ID` is correctly provided; observe init logs.

Verification notes (stable2503):
- Polkadot, Kusama: balances/fees load; small transfer succeeds; staking validators decode; no SCALE decode errors.
- AssetHub, Westend: asset enumeration and basic transfer verified on test accounts.
