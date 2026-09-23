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
