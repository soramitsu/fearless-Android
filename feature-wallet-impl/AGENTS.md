# AGENTS Guide: feature-wallet-impl

Purpose
- Implements wallet user flows: balances, send/receive, history, manage assets.
- Depends on `feature-wallet-api` for interfaces/models and on shared foundations (`runtime`, `core-db`, `common`).

Key Entry Points
- Send: `presentation/send/setup/SendSetupFragment|ViewModel`, `presentation/send/confirm/ConfirmSendFragment|ViewModel`.
- CBDC send (if enabled): `presentation/send/setupcbdc/*`.
- Receive: `presentation/receive/ReceiveFragment|ViewModel`, `ReceiveScreen.kt`.
- Manage assets: `presentation/manageassets/ManageAssetsFragment|ViewModel` and related Compose UI (e.g., `AssetsList.kt`).
- History: `presentation/history/AddressHistoryFragment|ViewModel`.
- Shared: `presentation/SendSharedState.kt`, `TransferDraft.kt`.

Integration Points
- Asset/fee UI mixins from `feature-wallet-api` (`FeeLoaderMixin`, `AssetSelector*`).
- Chain data via `runtime` (balances, fees, XCM/XTransfers if applicable).
- Persistence via `core-db` DAOs and models.

Common Tasks
- Add a new send validation: extend validations under `feature-wallet-api` or add local preflight; update ViewModel.
- Adjust fee loading: use `FeeLoaderMixin` and ensure chain/asset context is correct.
- Update asset list item UI: modify `SwipeableAssetListItem.kt` and `AssetsList.kt`.
- Add history filter: extend models in `feature-wallet-api` and add ViewModel filter logic.

Known TODOs/Tech Debt
- `data/network/blockchain/balance/SubstrateBalanceLoader.kt`: remove hardcoded `chainAssetId` fallback; resolve from registry.

Run Module Tests
- `./gradlew :feature-wallet-impl:testDebugUnitTest` (or `testDevelopDebugUnitTest` if present).

Troubleshooting
- Fee mismatch: verify chain and asset context, existential deposit, and decimals.
- Missing balances: confirm chain is synced in `ChainRegistry` and DB has asset entries.

