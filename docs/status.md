# Status Summary

Last updated: 2026-07-11

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
- Secrets in CI: Stubbed publishable MoonPay keys, EVM providers, and history
  providers keep public CI stable. MoonPay server secrets are prohibited from
  Android builds; prefilled wallet URLs require a backend signer.
- Test aggregation: `runTest` now covers every module with Kotlin/Java sources
  under `src/test`, including `core-api`, passkey backup, and XCM. A dynamic
  membership audit and adversarial self-test prevent omitted, stale,
  commented-out, duplicated, or shadow task entries.
- Iroha send remains production-disabled. An explicit CI/local gate now
  materializes only the checksum-pinned `core-jvm` artifact into ignored build
  output, tests a Java-only Taira bridge plus Kotlin 2.1 isolation, independently
  verifies compact transaction hashing against a Rust/native diagnostic vector,
  and proves the app runtime has no staged SDK dependency. A closed immutable
  request seam now validates the exact Nexus Android wallet-smoke transaction
  metadata contract and gates its operator path to the canonical Minamoto
  endpoint. The Taira-only bridge rejects every non-empty metadata map; normal
  transfers retain empty metadata and production DI remains fail closed. A
  reviewed Nexus metadata codec, live Torii receipt,
  funded-network, provenance, Android device/R8, authoritative live registry and
  fee mapping, deployed-node compatibility, and key-residue gates remain blocked
  in `config/iroha-production-send-readiness.json`.
- Release integrity: the manual release workflow builds an explicit strict
  `v`-prefixed SemVer tag on `master`, requires a completed successful Android
  CI push run for the exact tagged commit, prohibits CI version mutation,
  verifies the single AAB and signing certificate, and emits pinned GitHub
  build provenance. All workflow actions are full-SHA pinned.
- Security lint: missing Credential Manager transport and trust-all TLS code
  are fatal. The passkey module exports the Play Services transport, while
  unused legacy Spongy Castle PKIX/PGP artifacts are replaced by only the
  provider primitives required by `fearless-utils`.
- Local validation: `scripts/validate-local.sh` runs the same checks and now installs Android platform/build-tools 36 to match the compile SDK, along with NDK r28 and platform-tools.
- WalletConnect/Reown SDK: BOM bumped to 1.6.9 (requires AGP 8.9.1 + compileSdk 36) so the bundled UniFFI native libs ship with 16 KB page alignment.
- WalletConnect Pay: dependency excluded (until Reown publishes 16 KB-native builds) to avoid packaging the `yttrium-wcpay` 4 KB libraries.
- Google Play 16 KB page-size compliance: Native bundles rebuilt with NDK r28, `readelf -l` verification runs in CI on sr25519/toolChecker libraries, and the Play Console warning is cleared.
- Native crypto rebuild tooling: `scripts/build-libsodium.sh` now auto-detects the host-specific `toolchains/llvm/prebuilt` directory (darwin/linux/windows) so libsodium can be rebuilt on non-macOS hosts without manual tweaks.
- Utils composite build: public CI checks out `soramitsu/fearless-utils-Android` at `7500809f33243ee47ecb2ec8563fc284ac4de0d6`, exercises the adversarial derived-tree self-test, verifies the exact origin/commit/index/worktree against pinned `HEAD` plus the committed library-only patch with `scripts/ensure-fearless-utils.sh`, and forces the local composite include. The guard rejects staged, tracked, untracked, partial-overlay, patch-overlap, and submodule drift without resetting a dirty checkout. `settings.gradle` keeps Gradle 9 shims for the upstream build until that repo upgrades.

## Runtime & Chains
- Default types/chains live under `runtime/src/main/assets`. Override types and debug chain discovery via `TYPES_URL_OVERRIDE`, `DEFAULT_V13_TYPES_URL_OVERRIDE`, and `CHAINS_URL_DEBUG_OVERRIDE`; release chain discovery is immutable.
- ChainRegistry coordinates runtime providers and connections. EVM handled via `EthereumEnvironmentConfigurator` and `EthereumConnectionPool`.

## Polkadot SDK Alignment
- Target: polkadot-stable2503 (prepared via override keys).
- How to align debug builds: set `TYPES_URL_OVERRIDE`, `DEFAULT_V13_TYPES_URL_OVERRIDE`, and `CHAINS_URL_DEBUG_OVERRIDE` to registries validated against stable2503. See `docs/samples/local.properties.stable2503`.
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
- 2026-07-10 verification: all shell policy/adversarial suites passed; the
  aggregate Android test graph passed 496 tests with 0 failures/errors (14
  intentional skips); app lint and debug assembly passed; the APK passed ZIP
  integrity plus negative MoonPay-secret and legacy trust-all-class scans.
- XCM release status remains fail-closed: 15 required executable routes are
  tracked, 34 advertised routes are discovery-only, and funded production
  evidence is still required before broad XCM release enablement.
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
- Config: set MoonPay publishable keys, EVM provider keys, and history provider
  keys in `local.properties` or env vars (see README). Never configure a
  MoonPay server secret in the app.
- WalletConnect: ensure `WALLET_CONNECT_PROJECT_ID` is correctly provided; observe init logs.

Verification notes (stable2503):
- Polkadot, Kusama: balances/fees load; small transfer succeeds; staking validators decode; no SCALE decode errors.
- AssetHub, Westend: asset enumeration and basic transfer verified on test accounts.
