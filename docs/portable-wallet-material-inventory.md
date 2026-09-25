# Android wallet material required for portable recovery

This inventory is a source audit of the Android candidate. It is not a portable
plaintext format and does not enable passkey recovery. The final iOS/Android
format must be reviewed together before either app writes it.

| Material | Durable Android source | Required restore property |
| --- | --- | --- |
| Wallet grouping and public identity | `MetaAccountLocal` in `core-db/model/MetaAccountLocal.kt` stores each wallet's separate Substrate, EVM and TON public identities, name, selection, order and backup state. `ChainAccountLocal` stores per-chain identity and crypto type. | Preserve each wallet and its exact public addresses, selection and order; verify against restored original keys. |
| Substrate root | `SubstrateSecretStore` stores a validated V3 `SubstrateSecrets` record by meta ID: optional entropy and seed, keypair including SR25519 nonce, and derivation path. | Preserve the exact original keypair, crypto type, seed/entropy availability and path; never infer that a mnemonic exists for a raw-key wallet. |
| EVM root | `EthereumSecretStore` stores a separately validated V3 `EthereumSecrets` record by meta ID: optional entropy and seed, keypair and path. Historical direct-key EVM wallets need not derive from the Substrate mnemonic. | Preserve the exact private/public key and address, plus honest recovery metadata for a standalone key. |
| Native TON root | `TonSecretStore` stores a validated V3 `TonSecrets` record by meta ID: TON seed bytes and original private/public key. `TonAccountRepository.create` creates a TON-only wallet from its native mnemonic. | Preserve native TON seed and V4R2 identity separately; do not reconstruct it from a BIP39/Substrate phrase. |
| Per-chain keys | `SecretStoreV2` stores `ChainAccountSecrets` under meta ID and account ID: optional entropy/seed, exact keypair/nonce and path. `AccountRepositoryImpl.getChainAccountSecrets` binds reads to the durable account identity and recovery guard. | Include every chain account whose key is not provably reproducible from a restored root; preserve its public identity and signing behavior. |
| Historical V1 source | `SecretStoreV1Impl` may retain a source type, mnemonic/seed/path and keypair. Runtime reads use `AccountRepositoryImpl.getSecuritySource` with public-identity validation and quarantine. | Do not discard recoverable material during migration; validate original identity before export or signing. |

The legacy Google backup path in `AccountInteractorImpl.saveGoogleBackupAccount`
builds one `DecryptedBackupAccount` indexed by a Westend address. It carries a
Substrate mnemonic or seed, an EVM private-key seed and two JSON exports. It
does not include TON or per-chain secrets and cannot represent a TON-only
wallet. The public `BackupService` compatibility implementation currently
rejects remote save/import, so the source path is not live recovery evidence.

The old importer chose a Substrate mnemonic first and returned before applying
`seed.ethSeed`. The repaired source routes a backed-up EVM private key through
the same durable wallet creation as the Substrate root. If that key equals the
normal mnemonic/path derivation, existing mnemonic export metadata is retained;
if it differs, the exact EVM key is stored without falsely claiming its entropy
comes from the Substrate mnemonic. The legacy backup wire shape is unchanged.

The separate passkey generation code can authenticate an encrypted envelope and
call `PasskeyBackupPlaintextWalletVerifier`, but the production wallet-owned
plaintext serializer/verifier and install path are not implemented. Completion
requires an agreed cross-platform format, lossless migration for all rows above,
locked-storage and interruption tests, real replacement-device restoration, and
original-key signing/export evidence before backup completion is allowed.

The Android `PortableWalletMaterialPreflight` remains the non-exporting coverage
gate. An additional internal `captureDraftPlaintext` path takes the
cross-store mutation lock, reads every V3 root and V2 chain secret through the
identity-validated repository, checks the durable public inventory again, and
returns one bounded local draft record. It preserves separate Substrate, EVM
and native TON roots, exact encoded keypairs and metadata, every chain-account
secret, wallet order/selection and favorite chains. It also enumerates bounded
active V1 address keys, rejects malformed aliases, duplicate ownership and
valid addresses without a durable wallet owner, and captures V1 source
material only through `AccountRepositoryImpl.getSecuritySource`, which enforces
the existing recovery guard and original-wallet cryptographic validation.
Unowned active keys remain untouched for explicit local reconciliation; they
cannot be silently omitted from an eligible portable draft.
The inventory reads key names without decryption; guarded secret reads may
durably quarantine corrupt secrets under the existing wallet safety policy.
V1 source type, original SS58 address, exact keypair/nonce, optional seed and
path, mnemonic and its validated derived entropy occupy a separate slot; no
V3 root or V2 key is overwritten. V1 storage has no original entropy field,
so entropy is derived only when its mnemonic is present. Backup status and the old
Google backup address remain local operation state and are not trusted from the
record. Root and chain secret bytes are cleared after encoding; callers own
and must erase the returned plaintext. The strict draft decoder rejects
unknown versions, truncation, trailing fields, duplicate/unordered identities,
oversized fields and public-key mismatches.

The draft is Android-only and has no Drive/upload or restore/installer call
site. It is **not** the reviewed iOS/Android plaintext contract and cannot
establish portable recovery. Watch-only cohorts still fail closed.
Its semantic secret slots are explicit, though their current bytes use Android
SCALE schemas and are not iOS-readable:

| Draft slot | Exact validated Android material currently retained |
| --- | --- |
| `substrateSecret` | V3 `SubstrateSecrets`: optional original entropy, optional seed, public/private keypair, optional SR25519 nonce, optional derivation path. |
| `legacySubstrateSource` | V1 source type, exact original SS58 address and keypair/nonce, optional seed, mnemonic-derived entropy and derivation path. Captured separately from any V3 root. |
| `ethereumSecret` | Independent V3 `EthereumSecrets`: optional original entropy and seed, public/private EVM keypair, optional derivation path. It remains present even when a Substrate phrase also exists. |
| `tonSecret` | V3 `TonSecrets`: original native TON seed bytes, private key and public key. It is never derived from the Substrate root. |
| `chainSecrets[i]` | V2 `ChainAccountSecrets` for the explicitly named chain/account/crypto identity: optional entropy and seed, public/private keypair, optional nonce and derivation path. |

The lock covers wallet mutations using `WalletSecretMutationCoordinator`; the
second durable-inventory read catches metadata changes outside that lock. A
reviewed cross-platform format, atomic restore with original-key
signing/export verification, interrupted-write tests and replacement-device
evidence remain required before backup completion can be enabled.

The current read-only `FPWMSM01` exporter preserves two wallet-specific
Android string preferences as ascending metadata TLVs. ID `10` is the exact
UTF-8 value at `wallet_selected_chain_id<Android wallet ID>`; ID `11` is the
exact UTF-8 value at `chain_select_filter_applied_<Android wallet ID>`. Omitted
TLV means the preference is absent. Present TLV with a `u16` length of zero
means the preference exists with an empty string. Each value is at most 2048
UTF-8 bytes, with no replacement of malformed text. The exporter captures
these under the wallet cross-store lock and compares the current values again
after original-key signing proof. Both signed and watch wallets retain them;
the values do not confer signing authority. The codec continues to reject
unknown, duplicate and unordered metadata IDs. Explicit asset-row display
settings remain outside this mapping and block verified capture.

The cross-platform watch-wallet fixture for this extension is 70 bytes: one
wallet with ID `0x33` repeated 16 times, position zero, name `watch`, metadata
`10 = sora` and `11 =` present-empty, and one EVM watch identity. Canonical
SHA-256 is `e8959e6aa11fd339fd92ddd65798beda0b6443fe1d1be60f6ad219e2d26c3477`.
It is a shared codec fixture, not an installed-wallet or Drive recovery proof.
