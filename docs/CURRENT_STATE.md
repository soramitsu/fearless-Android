# Current State

This document summarizes the current state of the codebase as observed in this repository. It is meant to help new contributors orient quickly.

## Supported Ecosystems
- Substrate/Polkadot: Present via `runtime` (metadata, types, connections) and used across wallet, staking, and crowdloan features.
- EVM (Ethereum-compatible): Present via `runtime` (`EthereumConnectionPool`) and environment configurator; history providers and API keys are configurable via Gradle properties.
- TON: Present via `runtime` TON utilities and `core-db` tables; TON Connect flows live in dedicated feature modules.

## Key Features Present
- Wallet (send/receive/manage assets/history): `feature-wallet-api` and `feature-wallet-impl`.
- Account & Onboarding: `feature-account-*`, `feature-onboarding-*`.
- Staking & Crowdloans: `feature-staking-*`, `feature-crowdloan-*` (Substrate-centric).
- Swaps & Pools: `feature-polkaswap-*`, `feature-liquiditypools-*`.
- NFTs: `feature-nft-*`.
- Connectors: `feature-walletconnect-*` (WalletConnect v2), `feature-tonconnect-*` (TON Connect).

These features vary in maturity; consult TODOs below and module code for specifics.

## Global Initialization
- `App` (`jp.co.soramitsu.app.App`) configures language/locale, sets `OptionsProvider` (build info), and initializes WalletConnect v2.
- BuildConfig fields provide IDs/secrets required by external integrations (e.g., WalletConnect project ID).

## Configuration & Secrets
- Place integration keys in environment variables or `local.properties` as described in README.
- Common keys include Moonpay, Ethereum blast API keys, and Etherscan/Polygonscan keys.

## Build Types
- `debug`, `release`, `staging`, `develop`, `pr` — see `app/build.gradle` for differences (R8/shrinker, suffixes, Firebase App Distribution setup on CI builds).

## Runtime Types & Chains
- Default types and chain metadata are embedded under `runtime/src/main/assets`.
- You can override types/chains with `TYPES_URL_OVERRIDE`, `DEFAULT_V13_TYPES_URL_OVERRIDE`, and `CHAINS_URL_OVERRIDE` properties (see AGENTS.md/README).

## Known TODO/FIXME Hotspots
Ripgrep shows TODO/FIXME markers in these areas (non-exhaustive):
- Account models and meta-account adoption
- NFT details screen placeholders
- Crowdloan interactors (Karura) TODO marker
- Error text TODOs (`FearlessException`)
- Substrate balance loader hardcoded defaults
- Staking validator oversubscription/slashed logic FIXME

These markers indicate areas where behavior may be incomplete or needs refinement. Use `rg -n "TODO|FIXME"` to explore further.

## What To Verify Locally
- Secrets and endpoints present: Without keys, some flows (Moonpay, history providers) won’t fully function.
- Utils integration: The build maps `soramitsu/fearless-utils-Android` as a source dependency; no local path required.
- Android SDK/NDK and JDK versions: See README and `scripts/validate-local.sh`.
