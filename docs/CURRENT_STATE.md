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
- Common keys include Ethereum blast API keys and Etherscan/Polygonscan keys.
- MoonPay's legacy client-side URL signature and embedded signing secret are
  removed. The reviewed Android flow uses only publishable keys, an exact host
  allowlist, and manual wallet entry; prefilled wallet addresses remain
  unavailable until URL signing is moved to a backend-held secret.
- Production Firebase and upload-key material are never combined. A
  credential-free `release-controls` job validates the signed tag, exact
  source tree, governance, and prior CI before `release-build` restores
  Firebase. The upload key appears only in a separate fresh
  `release-signing` job. Gradle builds only the source-bound unsigned AAB and
  never receives signing or Play credentials. Signing uses a standalone
  certificate-pinned signer only after all four downloaded files, provenance,
  attestations, tag, `master`, and source tree pass validation. CI never
  receives a Play credential or mutates Play; an authorized operator uploads
  the final attested AAB through Play Console.
- CI/release dependency resolution is strict and pinned by the Gradle
  distribution checksum, dependency-verification metadata plus its tracked
  digest, the strict root buildscript-classpath lockfile, and the production
  release lockfile. Local Maven repositories and provenance rewrites are
  rejected for release graphs.

## Build Types
- `debug`, `release`, `staging`, `develop`, `pr` — see `app/build.gradle` for differences (R8/shrinker, suffixes, Firebase App Distribution setup on CI builds).

## Runtime Types & Chains
- Default types and chain metadata are embedded under `runtime/src/main/assets`.
- You can override types and debug chain discovery with `TYPES_URL_OVERRIDE`, `DEFAULT_V13_TYPES_URL_OVERRIDE`, and `CHAINS_URL_DEBUG_OVERRIDE` (see AGENTS.md/README). Release chain discovery is pinned and has no override.

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
- Secrets and endpoints present: Without keys, some history-provider flows won’t fully function.
- Utils integration: The build uses a pinned `soramitsu/fearless-utils-Android` checkout as a composite build. CI checks it out automatically; local builds should clone it next to this repo or set `FEARLESS_UTILS_PATH`.
- Android SDK/NDK and JDK versions: See README and `scripts/validate-local.sh`.
- Release controls: run the non-secret guard suites documented in
  `docs/releases/PROCESS.md`. Do not materialize live signing or Play
  credentials for routine local validation. Do not use an API or Gradle Play
  publishing task; the final AAB is uploaded manually through Play Console.
