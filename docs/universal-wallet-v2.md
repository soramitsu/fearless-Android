# Universal Wallet V2 Contract

Status: implementation contract for the Bitcoin, Solana, TON, and SORA Nexus
workstreams.

## Goals

- New wallets are one encrypted universal wallet, not one wallet per ecosystem.
- The default backup phrase for newly created wallets is 24 BIP39 words.
- Import remains compatible with 12-word BIP39 phrases.
- One phrase deterministically restores every supported account on Android, iOS,
  and the browser extension.
- Legacy accounts remain available only for export and fund-safety recovery after
  the migration cutoff.
- Wallet core, chain registry defaults, derivation rules, and indexer clients
  stay open source.

## Ecosystems

The shared ecosystem enum is lowercase at API, registry, and fixture boundaries:

- `substrate`
- `evm`
- `ton`
- `bitcoin`
- `solana`
- `iroha`

Platform UI may map these values to native enum names, but persisted and networked
interfaces must use the lowercase identifiers above.

## Identity Envelope

Wallet metadata uses a versioned JSON envelope with these fields:

| Field | Requirement |
| --- | --- |
| `schemaVersion` | Must be `2`. |
| `walletId` | Stable local id matching `uw2_[A-Za-z0-9_-]{16,64}`. |
| `displayName` | Non-empty user-visible name, at most 64 characters, no control characters. |
| `source` | One of `created-24-word`, `imported-12-word`, `imported-24-word`, `legacy-import`. |
| `status` | One of `active`, `migration-required`, `legacy-export-only`. |
| `publicAccounts` | Public account descriptors only; no mnemonic, seed, private key, or encrypted secret material. |
| `createdAtMillis` | Positive Unix epoch timestamp in milliseconds. |
| `updatedAtMillis` | Optional Unix epoch timestamp in milliseconds, greater than or equal to `createdAtMillis`. |
| `legacyExportOnlyReason` | Required only for `legacy-export-only`; otherwise must be absent or blank. |

An `active` identity must include at least one public account for every supported
ecosystem: `substrate`, `evm`, `ton`, `bitcoin`, `solana`, and `iroha`.
`migration-required` identities may be partial but must contain at least one
public account. `legacy-export-only` identities must use `source =
legacy-import` and include a short human-readable recovery/export reason.

Public account descriptors use lowercase ecosystem ids, stable account ids
matching `^[a-z0-9][a-z0-9._:-]{1,63}$`, chain ids matching
`^[A-Za-z0-9._:-]{2,128}$` when present, derivation paths rooted at `m`, and
lowercase hex public keys when present.

## Signing Contract

Wallet signing prompts use a shared request/result envelope. Requests contain
`requestId`, `accountId`, `ecosystem`, `chainId`, `origin`, `method`,
chain-specific payload fields, `createdAtMillis`, and optional
`expiresAtMillis`.

Allowed methods are `sign-message`, `sign-transaction`,
`sign-and-send-transaction`, and `sign-all-transactions`. Message payloads use
`base64`, `hex`, or `utf8` encoding plus a display hint of `raw`, `utf8`, or
`hex`. Single transaction methods carry `transactionBase64`. Batch signing
carries `transactionsBase64` with at most 16 transactions.

Results contain the request identity fields, method, `status`, signing outputs,
optional `errorCode`, and `signedAtMillis`. Allowed statuses are `approved`,
`rejected`, and `failed`. Approved message results must include a public key and
at least one signature representation. Approved transaction results must include
the signed transaction payload. Approved sign-and-send results must also include
a transaction hash. Rejected and failed results must include an error code and
must not include signatures or signed payloads.

Clients must reject malformed request ids, account ids, ecosystems, chain ids,
origins, timestamps, invalid base64/hex payloads, oversized transaction batches,
and result payloads that do not match their status and method.

## Hard-Cutoff Migration

After the migration cutoff, normal wallet access is allowed only when a valid
Universal Wallet V2 identity exists. A wallet with legacy accounts and no
universal wallet must enter `migrate-before-access`: normal balance, transfer,
staking, dApp, and signing flows stay blocked until the user creates or imports a
universal wallet. A fresh install with no legacy material enters
`create-universal-wallet`.

Legacy vault descriptors are export-only. They may expose backup/export and
fund-safety recovery metadata, but they must set `canExportSecrets = true`,
`canSignTransactions = false`, and `mode = export-only`. Legacy vaults must not
be used for new signatures, broadcasts, staking actions, dApp approvals, or
normal account selection after the cutoff.

Migration snapshots contain `schemaVersion`, `platform`, `hasUniversalWallet`,
`legacyVaults`, `cutoffAtMillis`, and `evaluatedAtMillis`. Clients must reject
duplicate legacy vault ids, malformed account ids, unknown ecosystems, unsafe
addresses or display names, missing export-only reasons, disabled export, legacy
signing flags, and invalid timestamps.

## Secure-Storage Migration Requirements

Android migration must read old account material only through the existing
encrypted stores, write Universal Wallet V2 material through Android Keystore
backed encryption where available, preserve export-only legacy descriptors, and
never copy mnemonic, seed, or private-key material into public logs, analytics,
plain preferences, or repo fixtures.

iOS migration must read old account material only through Keychain-backed
storage, write Universal Wallet V2 material to the configured Keychain access
group with passcode or biometric protection where available, preserve export-only
legacy descriptors, and avoid storing secret material in Core Data, user defaults,
logs, or public fixtures.

Web extension migration must read old account material through the extension
keyring, write Universal Wallet V2 material only through the encrypted keyring or
WebCrypto-protected browser storage, preserve export-only legacy descriptors, and
avoid storing secret material in unencrypted local storage, background messages,
Redux/Pinia state, logs, or test fixtures.

## Derivation Defaults

| Ecosystem | Default |
| --- | --- |
| Substrate/DOT | Existing sr25519 root derivation; SS58 prefix is chain-specific. |
| EVM | `m/44'/60'/0'/0/0` |
| Bitcoin mainnet | BIP84 account path `m/84'/0'/0'`; first receive path `m/84'/0'/0'/0/0`. |
| Bitcoin testnet | BIP84 account path `m/84'/1'/0'`; first receive path `m/84'/1'/0'/0/0`. |
| Solana | `m/44'/501'/0'/0'` |
| TON | `m/44'/607'/0'/0'/0'`, Wallet V4 R2, workchain `0`. |
| SORA Nexus/Iroha | `m/44'/617'/0'/0'`, Ed25519 I105 account address. |

Solana import compatibility may support common existing Solana paths, but new
Universal Wallet V2 accounts must use `m/44'/501'/0'/0'`.

## Registry Requirements

Registry files use `schemaVersion = 1` and a top-level `chains` array. Each
chain entry contains `id`, lowercase `ecosystem`, `chainId`, `displayName`,
`enabledByDefault`, optional `nativeAsset`, optional `derivationPath`, optional
`slip44CoinType`, and `endpoints`.

Enabled-by-default chains must include at least one endpoint. Disabled or gated
chains, including Nexus before its production Torii/TLS endpoint is confirmed,
may omit endpoints. Chain ids and endpoint ids must be unique, display names
must be non-empty human-readable strings without control characters, native asset
symbols must be uppercase, decimals must be in `0...255`, and derivation paths
must be rooted at `m`.

Endpoint kinds are `indexer`, `rpc`, `torii-mcp`, and `explorer`. Public URLs
must be HTTPS; local development URLs may use `http://localhost` or
`http://127.0.0.1`. Public indexer endpoints are read-only. Broadcasts,
transaction simulation, and other write operations must use RPC or Torii
endpoints, never a public indexer endpoint.

- TON indexer base URL: `https://ti.soramitsu.io`.
- Solana indexer base URL: `https://si.soramitsu.io`.
- `si.soramitsu.io` is read-only. Transaction simulation and broadcast use the
  configured Solana RPC endpoint directly.
- Taira testnet is enabled with I105 chain discriminant `369`, Torii root
  `https://taira.sora.org`, and chain id `iroha3-taira`.
- Nexus mainnet uses I105 chain discriminant `753` and chain id
  `sora:nexus:global`, but remains registry-gated until the
  production Torii/TLS endpoint is confirmed.

## Normalized Indexer Contract

Wallet-facing indexer code maps raw chain responses into normalized public
payloads before UI, cache, or migration logic consumes them.

Normalized asset balances contain `accountId`, `ecosystem`, `chainId`,
`assetId`, raw integer `amount`, `decimals`, `isNative`, optional `symbol`,
optional `name`, optional `uiAmountString`, optional token or contract account
ids, and `syncedAtMillis`.

Normalized transactions contain `accountId`, `ecosystem`, `chainId`,
`transactionId`, `status`, `direction`, `operationType`, optional timestamp,
optional raw integer amount and fee fields, optional counterparty, optional block
number, optional cursor, optional HTTPS explorer URL, and `syncedAtMillis`.
Allowed transaction statuses are `pending`, `confirmed`, and `failed`. Allowed
directions are `incoming`, `outgoing`, `self`, and `unknown`. Allowed operation
types are `transfer`, `swap`, `stake`, `unstake`, `governance`,
`contract-call`, `mint`, `burn`, `fee`, and `unknown`.

Normalized token metadata contains `ecosystem`, `chainId`, `assetId`,
`decimals`, optional `symbol`, optional `name`, optional `iconUrl`, optional
`metadataUrl`, `isVerified`, and `syncedAtMillis`. Public asset URLs must be
HTTPS or IPFS URLs. Page metadata contains optional `nextCursor`, `limit`,
optional `total`, and `syncedAtMillis`; page limits are capped at `250`.

All normalized amounts are decimal strings containing unsigned raw integer
amounts. The clients must reject malformed account ids, unknown ecosystems,
malformed chain ids, unsafe cursors, invalid URLs, non-integer amounts, invalid
decimals, and non-positive sync timestamps before persisting normalized data.

## Fixture Contract

Golden vectors live in `common/src/test/resources/universal-wallet-v2-vectors.json`.
Every wallet repo must keep an equivalent fixture and test that:

- validates all six ecosystem sections are present;
- derives each expected public address from the mnemonic and path;
- rejects wrong Bitcoin purpose paths;
- rejects wrong Solana paths;
- rejects TON testnet-only addresses for mainnet receive flows;
- rejects Taira and Nexus I105 discriminants when used interchangeably.

The fixture intentionally contains a 12-word import vector and a non-zero
24-word default-wallet vector so test suites catch both compatibility and new
wallet creation regressions.
