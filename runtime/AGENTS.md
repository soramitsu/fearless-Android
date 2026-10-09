# AGENTS Guide: runtime

Purpose
- Manages chain metadata, types, and network connectivity for Substrate, EVM, and TON.
- Provides ChainRegistry to coordinate runtimes, connections, and sync lifecycle.

Key Components
- `multiNetwork/ChainRegistry.kt` — starts/stops chain runtimes, observes DB to decide which chains to sync; node switching.
- `multiNetwork/connection/ConnectionPool.kt` — Substrate connections; `EthereumConnectionPool.kt` for EVM.
- `multiNetwork/runtime/*` — Runtime providers and subscriptions.
- `multiNetwork/configurator/*` — Environment configurators for Substrate/EVM/TON; selects how to set up connections per ecosystem.
- Assets & types: `src/main/assets/metadata/*`, `src/main/assets/types/*.json`, and `local_chains.json`.

Overrides (align debug builds with specific SDK/runtime sources)
- In `local.properties` or env: `TYPES_URL_OVERRIDE`, `DEFAULT_V13_TYPES_URL_OVERRIDE`, `CHAINS_URL_DEBUG_OVERRIDE`.
- Release `CHAINS_URL` is immutable and does not accept an override.
- After changes, run: `./gradlew detektAll runTest :app:lint`.

Common Tasks
- Start syncing chains: call `ChainRegistry.syncUp()` from the owning lifecycle (app/feature).
- Switch node: use `ChainRegistry.switchNode(NodeId)`; UI often lives in account settings.
- Add a new chain (Substrate): add to chain sources and ensure types; verify `RuntimeProvider` comes up.
- Add an EVM chain: ensure `EthereumEnvironmentConfigurator` handles it and `EthereumConnectionPool` can connect.
- TON specifics: utilities under `chain/ton/*` including wallet contracts and payloads.

Troubleshooting
- Not syncing: check that chain has nodes in DB and that connection pool is not paused.
- Runtime not available: ensure types for chain are correct; look at logs for `RuntimeProvider`.
- EVM connection down: verify RPC URL and that `EthereumConnectionPool.setupConnection` succeeded.

Tests
- Prefer lightweight tests around `ChainRegistry` using fakes for DAO/repositories.
- Add migration tests in `core-db` for any schema impacting runtime selection.
