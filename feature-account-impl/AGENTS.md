# AGENTS Guide: feature-account-impl

Purpose
- Implements account management, meta-accounts, address book, and node management screens.
- Bridges account data with chain-specific requirements (Substrate, EVM, TON).

Key Spots
- Data sources/repositories under `impl/data/repository/*` and `impl/data/repository/datasource/*`.
- Node management UI: `presentation/options_switch_node/OptionsSwitchNodeContent.kt`.
- Address book screens and viewmodels live under `presentation/addressbook/*`.

Integration Points
- Uses `core-db` for accounts, nodes, and address book tables.
- Coordinates with `runtime` for node switching via `ChainRegistry.switchNode`.

Common Tasks
- Add derivation path support: extend account repository/data sources and DB fields as needed.
- Implement/adjust meta-account logic: ensure `accountId(chain)` is null-safe for EVM chains.
- Node management polish: unhide/enable switch button when behavior is finalized.

Known TODOs/Tech Debt
- `api/presentation/account/AddressDisplayUseCase.kt`: adopt meta-account logic.
- `api/domain/model/Account.kt`: consider making `cryptoType` optional.
- `impl/data/repository/datasource/AccountDataSource.kt`: compatibility-only path; review necessity.
- `presentation/options_switch_node/OptionsSwitchNodeContent.kt`: button temporarily hidden.

Tests
- `./gradlew :feature-account-impl:testDebugUnitTest`
- Add ViewModel tests using coroutines test; mock repositories/ChainRegistry for node flows.

