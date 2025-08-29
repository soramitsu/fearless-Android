# Status Summary

Last updated: 2025-08-22

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
- Sora Card: Present but requires credentials via Gradle props.
- NFTs: Present; details screen has TODO placeholders.

## Build & CI
- CI Pipeline: `.github/workflows/android-ci.yml` runs detekt, unit tests (`runTest`), and app lint on push/PR.
- Secrets in CI: Stubbed keys for Moonpay, EVM providers, and history providers to keep resolution stable; real keys required locally.
- Local validation: `scripts/validate-local.sh` runs the same checks and ensures SDK packages.

## Runtime & Chains
- Default types/chains under `runtime/src/main/assets`. Override via `TYPES_URL_OVERRIDE`, `DEFAULT_V13_TYPES_URL_OVERRIDE`, `CHAINS_URL_OVERRIDE` in `local.properties`.
- ChainRegistry coordinates runtime providers and connections. EVM handled via `EthereumEnvironmentConfigurator` and `EthereumConnectionPool`.

## Polkadot SDK Alignment
- Target: polkadot-stable2503 (prepared via override keys).
- How to align: set `TYPES_URL_OVERRIDE`, `DEFAULT_V13_TYPES_URL_OVERRIDE`, and `CHAINS_URL_OVERRIDE` to registries validated against stable2503. See `docs/samples/local.properties.stable2503`.
- Optional: pin `shared_features` via `SHARED_FEATURES_VERSION_OVERRIDE=1.x.y` if required by the SDK combo.
- Utils integration: the build fetches `soramitsu/fearless-utils-Android` as a source dependency and builds it from source.
- Debug: run `./gradlew printPolkadotSdkAlignment` to verify effective overrides.

## Health & Risks (Snapshot)
- Code quality: Detekt enforced in CI. Several TODO/FIXME markers remain in features and common utils.
- Incomplete UI/logic areas:
  - NFT details screen placeholders.
  - Sora Card details screen multiple TODOs.
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
- Sora Card:
  - `feature-soracard-impl/.../SoraCardDetailsScreen.kt` — multiple TODO placeholders.
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
- Secrets: set Moonpay, EVM provider keys, and history provider keys in `local.properties` or env vars (see README).
- WalletConnect: ensure `WALLET_CONNECT_PROJECT_ID` is correctly provided; observe init logs.

Verification notes (stable2503):
- Polkadot, Kusama: balances/fees load; small transfer succeeds; staking validators decode; no SCALE decode errors.
- AssetHub, Westend: asset enumeration and basic transfer verified on test accounts.
