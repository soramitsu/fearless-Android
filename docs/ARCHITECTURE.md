# Architecture Overview

This document explains how the Android app is structured, how modules interact, and where to look when making changes.

## Layers & Composition
- UI & Navigation: Lives in `app/` and `feature-*/impl` modules. UI uses Android Views and Jetpack Compose where applicable. Navigation is driven via `NavHost` graphs under `app/src/main/res/navigation` and `RootActivity` (`jp.co.soramitsu.app.root.presentation.RootActivity`).
- Features: Each feature has a pair of modules: `feature-<name>-api` (interfaces, contracts, lightweight models) and `feature-<name>-impl` (DI wiring, repositories, interactors, ViewModels, UI). The `app` module depends on feature `-api` and `-impl` modules.
- Shared Foundations:
  - `common/`: UI base classes, utilities, error handling, QR scanning, small domain helpers.
  - `core-api/`: Core interfaces and models used across features (runtime config, storage abstractions).
  - `core-db/`: Room database, DAOs, entities, and migrations.
  - `runtime/`: Chain metadata, connections, runtime files, and multi-ecosystem (Substrate/EVM/TON) integration.
  - `runtime-permission/`: Lightweight runtime permissions helper library.
  - `test-shared/`: Test utilities reused by multiple modules.

## Dependency Injection
- DI is powered by Hilt. The `App` class (`jp.co.soramitsu.app.App`) is annotated with `@HiltAndroidApp` and bootstraps DI.
- Feature `-impl` modules usually declare Hilt modules/components for their screens and interactors.

## Navigation
- Entry point: `RootActivity` and `main_nav_graph.xml` (plus `root_nav_graph.xml`, `onboarding_nav_graph.xml`, `bottom_nav_graph.xml`).
- Features expose routers or navigator interfaces from their `-api` modules to decouple navigation from implementations.

## Data Flow & Persistence
- Storage: `core-db` hosts the Room database (`AppDatabase`) with DAOs like `AssetDao`, `ChainDao`, `MetaAccountDao`, etc. Migrations are under `core-db/src/main/java/.../migrations`.
- Networking & Chain Runtime: `runtime` manages chain metadata and connections:
  - `ChainRegistry` coordinates runtime providers, subscriptions, and connections per chain.
  - Connection pools for Substrate and EVM (`ConnectionPool`, `EthereumConnectionPool`).
  - TON utilities and contracts are available under `runtime/.../chain/ton`.
  - Local metadata/type files are under `runtime/src/main/assets` and can be overridden via properties (see AGENTS.md / README for overrides).

## Ecosystems
- Substrate/Polkadot: Runtime metadata, types, and websocket connections managed under `runtime`.
- EVM (Ethereum, BSC, Polygon, etc.): EVM chains are configured via `EthereumEnvironmentConfigurator` and managed in `EthereumConnectionPool`.
- TON: Supported via TON-specific remote sources, contracts, and DB tables.

## External Integrations
- WalletConnect v2: Initialized in `App.setupWalletConnect()` using the Reown WalletConnect SDK. Project ID set via `BuildConfig.WALLET_CONNECT_PROJECT_ID`.
- TON Connect: Dedicated feature modules `feature-tonconnect-api` and `feature-tonconnect-impl` with persistence in `core-db` (`TonConnectDao`).
- Sora Card: `feature-soracard-*` modules; requires credentials via Gradle properties.

## Build Types & Tooling
- Build types: `debug`, `release`, `staging`, `develop`, `pr`. R8/shrinker is enabled on remote builds for closer prod parity.
- Static analysis: Detekt (`./gradlew detektAll`) with formatting (`detektFormat`).
- Unit tests: `./gradlew runTest` aggregates checks and test reports.
- Utils integration: `settings.gradle` maps the GitHub repo `soramitsu/fearless-utils-Android` as a source dependency (via `sourceControl`).

## Where To Start
- App lifecycle and global initialization: `app/src/main/java/jp/co/soramitsu/app/App.kt`.
- Navigation: `app/src/main/res/navigation/*` and `RootActivity`.
- Chains and connections: `runtime/multiNetwork/*` and `core-db` chain entities.
- Wallet features (send/receive/history): `feature-wallet-api` and `feature-wallet-impl`.
- Accounts/onboarding: `feature-account-*`, `feature-onboarding-*`.
