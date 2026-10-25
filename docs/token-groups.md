# Curated token groups and shared portfolio lists (4.2.0)

Android, iOS and the extension share one portfolio contract and three data files. The canonical
copies, their JSON Schemas, the validator and the contract (`docs/portfolio-contract.md`) live in
the workspace root repository; this repository only bundles byte copies.

| Root file | Android copy | Used for |
|---|---|---|
| `config/token-groups/canonical-asset-groups.v1.json` | `runtime/src/main/assets/canonical-asset-groups.v1.json` | curated groups (resolved by `TokenGroupRegistry`; no screen renders a combined row yet) and the `untrustedAssets` price list |
| `config/portfolio/testnet-chain-ids.v1.json` | `runtime/src/main/assets/testnet-chain-ids.v1.json` | testnets without the registry `testnet` option |
| `config/portfolio/default-visible-at-zero.v1.json` | `runtime/src/main/assets/default-visible-at-zero.v1.json` | the listed part of the zero rule |

`runtime/src/test/.../tokengroups/SharedPortfolioConfigPinTest.kt` pins each copy by sha256, and
`runtime/src/test/resources/portfolio/chains-v13-20261009.json` is the registry snapshot the files
were reviewed against (its sha256 is the files' `registry.snapshotSha256`).

## Rules the app applies

- Bundled only. There is no remote copy in 4.2.0; a future override needs a signature.
- Only `approved` groups render. Matching is exact on (registry chainId, registry assetId); EVM
  contract ids compare case-insensitively, every other id exactly. Nothing is grouped by symbol or
  priceId alone, and a group never supplies a price.
- A member is active only if its chain is present once, is not a testnet (registry option or the
  shared list) and has exactly one verified asset with that id whose symbol (or a reviewed alias),
  precision and priceId still match the reviewed values. Inactive members render as their own rows.
- A member listed twice, in any groups, invalidates every group containing it. An approved group
  whose members declare different precisions never renders. A group needs two active members.
- Each file loads on its own. If one is unusable the app falls back: no groups, the registry
  `testnet` option alone, or the computed zero rule.
- `untrustedAssets` in the groups file lists registry assets whose registry price must not be
  trusted (today Kusama AssetHub Assets id 11, a third-party "USDT" the registry prices as
  Tether). `PortfolioVisibility.hasTrustedPrice` is false for a listed asset whatever `priceId` the
  registry gives it. `WalletRepositoryImpl` and `TokenRepositoryImpl` drop its rate where a stored
  price becomes a domain token, so no screen shows or sums a value for it; the list row says
  "Price unavailable", the totals count it under "N assets without price not included", and a
  group that lists it never renders. The list is still read when the catalog itself is rejected
  (`UntrustedAssets.salvage`), so an unreadable group cannot bring the price back.

Code: `runtime/src/main/java/jp/co/soramitsu/runtime/multiNetwork/tokengroups/`
(`TokenGroupRegistry` is provided by `ChainRegistryModule`).

## Visibility, the zero rule and totals

`PortfolioVisibility` (same package, provided by `ChainRegistryModule`) is the one rule the
portfolio list, Manage assets, Search, the asset pickers, the totals and background sync use
(contract §3-4):

- `assets.enabled` is tri-state: `1` explicit Shown, `0` explicit Hidden, `NULL` Auto. Only user
  actions write Shown or Hidden.
- `visible = Hidden ? false : Shown ? true : (total > 0 || isZeroDefault)`. Explicit Shown is
  honoured at zero, as 4.2.0 displayed it.
- `isZeroDefault` = not a testnet, sending not vetoed (`SendPausePolicy`), and either listed in
  the zero file or the primary utility asset (first `isUtility`, else first `isNative`) of a
  favourite network or of the single network the user selected. Rows exist only for networks the
  wallet holds an account on. Without a usable zero file the computed rule applies: the primary
  utility asset of a ranked mainnet. App-owned networks match under either id (`bitcoin-mainnet`
  or `bitcoin:mainnet`).
- Testnets (registry option or the shared list) sit behind the per-wallet "Show testnets" switch
  in Manage assets (`portfolio.show_testnets.v1.<metaId>`), except when the user selects that one
  network. They are never zero defaults and never counted. A testnet token never carries a price
  either, whatever priceId the registry gives it (Canary Matrixchain cENJ is priced as Enjin
  Coin): `PortfolioVisibility.hasTrustedPrice(asset)` is false for it, the repositories drop its
  rate like an untrusted asset's, and its row says "No value" instead of "Price unavailable".
- The header total, network subtotals and network order count every holding that is neither
  hidden nor on a testnet; values are `free + reserved` times the asset's own trusted registry
  price. Rows show the same total, with a "Transferable" line when part of it cannot be sent, and
  Manage assets shows the total too.
  `TotalBalance.hiddenBalance` carries the hidden part. Wallet-management, backup and wallet
  switcher cards show `allHoldingsBalance` labelled "incl. hidden"; the portfolio header shows
  `balance`, and Profile adds "N hidden assets not included".
- What the total leaves out is labelled under the list (`PortfolioExclusions`, built by
  `AssetListHelper.portfolioExclusions`): "N hidden assets not included · Manage",
  "N assets without price not included" and "Pool stakes not included" (a counted native holding
  on a network with nomination pools). The values are wallet-wide like the total, whatever
  network filter the list has, and count verified assets only: a detected token is never
  described as missing value.
- Hidden assets that hold funds are announced once by the "N hidden assets hold funds" card above
  the list (Review opens Manage assets, Keep hidden acknowledges them). 4.2.0 hid every empty
  asset of a funded wallet and never revealed a later deposit, and database 81 keeps such a funded
  row hidden, so this is how an upgraded wallet finds that money. Assets the user hides while
  they hold a balance are acknowledged at once; a later deposit into another hidden asset is
  announced again (`portfolio.hidden_funded_reviewed.v1.<metaId>`).
- Manage assets lists hidden funded assets first, with their amount. A hide that already existed
  when this version first listed the wallet (`portfolio.legacy_hidden.v1.<metaId>`, written once)
  is labelled "Hidden in previous version", any other "Hidden".
- Background sync connects ranked mainnets plus every chain with a Shown asset or any funded
  asset, hidden ones included (`AssetDao.observeChainIdsNeedingSync`). A testnet is not connected
  for its rank; Westend still is, because staking offers it.

`runtime/src/test/resources/portfolio/parity-cases.v1.json` is a byte copy of the root
`config/portfolio/parity-cases.v1.json`; `PortfolioVisibilityTest` pins its sha256 and reproduces
every zero-default case with the app's own rule. Copy it again when the root file changes.
The contract's untrusted-price conformance case runs against the reviewed registry snapshot in
`TotalBalanceUseCaseImplTest` (header), `AssetListHelperTest` and `BalanceListViewModelTests`
(rows and subtotals) and `AssetPriceTrustTest` (every other screen's price source).

### Search and the asset pickers
Released builds listed only `enabled = 1` rows in Search, the asset pickers and the Send network
field. Sync now stores every new row as Auto, so those lists use the portfolio rule instead:

- Search (`PortfolioVisibility.searchSection`): "In your portfolio" is exactly what the list
  shows; for a non-empty query it adds explicitly hidden assets (swipe to show them again) and
  "Other assets", up to 50 reviewed registry assets the portfolio does not show, so a token can be
  found before the first deposit. The query matches the symbol, the name and the network name.
  Testnets stay behind the Testnets switch and detected tokens are reviewed in the portfolio only.
  A result opens the exact (network, asset) detail; the old intermediate "networks of this asset"
  screen is deprecated and no longer opened.
- Pickers take an `AssetSelectPurpose`; a bundle without one is a Send picker. Send, swap "from"
  and cross-chain (`isPickableToSpend`) list any positive sendable balance, with hidden assets in a
  collapsed "Hidden assets" section, because hiding never blocks spending. Receive and swap "to"
  (`isPickableToReceive`) list every reviewed asset of the network, held or not. Entries read
  "SYMBOL · Network", and selection and exclusion compare (network, asset id): DOT's id is the same
  on Polkadot and Polkadot AssetHub.
- The Send network field (`ChainDao.observeChainsWithBalance`) offers networks where the token is
  Shown or held in an Auto row; explicitly hidden rows stay out.

Upgrade path: `core-db` `ReleasedWalletBalanceSyncUpgradeTest` and `feature-account-impl`
`ReleasedPortfolioUpgradeTest` (instrumented) start from the frozen 3.7.4 database with asset rows
in the states released builds wrote, replay every shipped migration through the production open,
and check background sync, the visible rows, the totals against the shipped total formula, and
what Search and the pickers list.

## Changing the data

Edit the root files only, then from the workspace root:

```sh
node scripts/validate-canonical-asset-groups.mjs
node scripts/sync-canonical-asset-groups.mjs --apps android --target android=<this checkout>
node scripts/sync-canonical-asset-groups.mjs --check --require-pins --apps android --target android=<this checkout>
```

The sync copies the files and rewrites the pin constants. When the root review moves to a new
registry snapshot, copy that snapshot into `runtime/src/test/resources/portfolio/` as well and run
`./gradlew :runtime:testDebugUnitTest`.
