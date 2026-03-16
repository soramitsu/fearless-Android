# Module Map

Short descriptions of modules to speed up discovery.

## App & Foundations
- `app/`: Android application module. Wires features, sets build types, and hosts navigation graphs and `RootActivity`.
- `common/`: Base UI classes, errors, utilities (QR, permissions helpers, Compose/VB helpers), domain helpers.
- `core-api/`: Core interfaces and models shared across modules (runtime configuration, storage abstractions, updaters).
- `core-db/`: Room database entities, DAOs, and migrations.
- `runtime/`: Chain registry, connection pools, runtime types and metadata, multi-ecosystem support (Substrate/EVM/TON).
- `runtime-permission/`: Small standalone runtime permission utility library (Java/Kotlin APIs).
- `test-shared/`: Test-only utilities shared by unit tests.

## Feature Modules
Each feature is split into `-api` (interfaces, contracts) and `-impl` (implementation) to reduce coupling.

- `feature-account-api` / `feature-account-impl`: Account management, meta-accounts, address book, node management.
- `feature-onboarding-api` / `feature-onboarding-impl`: First-time user flows, wallet creation/import.
- `feature-wallet-api` / `feature-wallet-impl`: Balances, send/receive, history, asset management, common wallet UI.
- `feature-staking-api` / `feature-staking-impl`: Staking flows for relay/parachains and staking pools.
- `feature-crowdloan-api` / `feature-crowdloan-impl`: Crowdloan contributions and related UI.
- `feature-polkaswap-api` / `feature-polkaswap-impl`: Swap functionality and related liquidity operations.
- `feature-liquiditypools-api` / `feature-liquiditypools-impl`: Liquidity pool management UIs.
- `feature-nft-api` / `feature-nft-impl`: NFT browsing/details.
- `feature-walletconnect-api` / `feature-walletconnect-impl`: WalletConnect v2 flows.
- `feature-tonconnect-api` / `feature-tonconnect-impl`: TON Connect flows.
- `feature-success-api` / `feature-success-impl`: Generic success/confirmation screens and flows.
- `feature-splash`: Simple splash/loading feature.

## Gradle & Scripts
- `buildSrc/`: Gradle convention plugins, versions catalogs wiring, and build logic.
- `scripts/`: Helper scripts (e.g., `validate-local.sh`) and Gradle snippets (e.g., `secrets.gradle`, versions setup).
- `detekt/`: Static analysis configuration.
