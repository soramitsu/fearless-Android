package jp.co.soramitsu.coredb.migrations.shipped

import jp.co.soramitsu.common.data.Keypair as ShippedKeypair
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.string
import jp.co.soramitsu.fearless_utils.scale.toHexString

/**
 * Test-only replicas of the secret writers released builds ran, so upgrade
 * tests start from what a shipped build stored instead of from payloads the
 * code under test produced.
 *
 * Provenance, fearless-Android tag 3.7.4 (commit 2da66a2cd):
 * - common/src/main/java/jp/co/soramitsu/common/data/secrets/v2/MetaAccountSecrets.kt:12-61,
 *   sha256 321bbd1b88f72b1921d25d2a80673b918ac16ad9ea6f1976dbca93f44e8412d5:
 *   KeyPairSchema, the six-field MetaAccountSecrets and its factory, verbatim.
 * - feature-account-impl/src/main/java/jp/co/soramitsu/account/impl/data/repository/AccountRepositoryImpl.kt:298-355,
 *   sha256 03fa8aa0247cafeb836fa7f67695ba407507965ab35a3e50e83eb2b3b78b5d66:
 *   importFromSeed, from key generation to the MetaAccountSecrets call and
 *   the MetaAccountLocal identity, without its Room and coroutine plumbing.
 *   The same lines are present from 2.0.2 through 3.7.4.
 * - common/src/main/java/jp/co/soramitsu/common/utils/FearlessLibExt.kt:35-36,
 *   sha256 020e837ecf268c3e584fcf0211ce48c29dd0d30e362290752d9fd9af7ef83281:
 *   DEFAULT_DERIVATION_PATH.
 *
 * Provenance, 4.2.0 production source (commit b5e22d49d):
 * - feature-account-impl/src/main/java/jp/co/soramitsu/account/impl/data/repository/AccountRepositoryImpl.kt:213-248,
 *   sha256 95dd373690988361fcb042b42b776b94343f9a696119dfac5164099cebe0dd1e:
 *   importAdditionalAccount, which added an Ethereum key to a pre-4.0 wallet.
 * - core-db/src/main/java/jp/co/soramitsu/coredb/migrations/TonMigration.kt:286-329,
 *   sha256 4ed6d79a22e5236fd419f2ff1327517554211dfc8e05c222baf40fb965a703d0
 *   (git blob da5238cf4c8e, the same in 4.0.1, 4.0.2, 4.1.1 and v4.2.0):
 *   migrateToSeparatedSecretsStorage, the secret half of the shipped 71 -> 72,
 *   with common/.../secrets/v3/SubstrateSecretStore.kt and
 *   EthereumSecretStore.kt (put, schema and factory) inlined.
 *
 * Only the library package changed (shared_utils -> fearless_utils), and
 * BouncyCastle's Hex.decode is replaced by the equivalent fromHex(); the
 * SCALE and key-factory sources are the same (android-keys.md section 1, A3),
 * and the production-parity golden vectors prove the encodings byte-equal.
 */
object ShippedSecretWriters {

    const val DEFAULT_DERIVATION_PATH = "//44//60//0/0/0"

    object KeyPairSchema : Schema<KeyPairSchema>() {
        val PrivateKey by byteArray()
        val PublicKey by byteArray()

        val Nonce by byteArray().optional()
    }

    object MetaAccountSecrets : Schema<MetaAccountSecrets>() {
        val Entropy by byteArray().optional()
        val Seed by byteArray().optional()

        val SubstrateKeypair by schema(KeyPairSchema)
        val SubstrateDerivationPath by string().optional()

        val EthereumKeypair by schema(KeyPairSchema).optional()
        val EthereumDerivationPath by string().optional()
    }

    fun MetaAccountSecrets(
        substrateKeyPair: Keypair,
        entropy: ByteArray? = null,
        seed: ByteArray? = null,
        substrateDerivationPath: String? = null,
        ethereumKeypair: Keypair? = null,
        ethereumDerivationPath: String? = null
    ): EncodableStruct<MetaAccountSecrets> = MetaAccountSecrets { secrets ->
        secrets[Entropy] = entropy
        secrets[Seed] = seed

        secrets[SubstrateKeypair] = KeyPairSchema { keypair ->
            keypair[PublicKey] = substrateKeyPair.publicKey
            keypair[PrivateKey] = substrateKeyPair.privateKey
            keypair[Nonce] = (substrateKeyPair as? Sr25519Keypair)?.nonce
        }
        secrets[SubstrateDerivationPath] = substrateDerivationPath

        secrets[EthereumKeypair] = ethereumKeypair?.let {
            KeyPairSchema { keypair ->
                keypair[PublicKey] = it.publicKey
                keypair[PrivateKey] = it.privateKey
                keypair[Nonce] = null // ethereum does not support Sr25519 so nonce is always null
            }
        }
        secrets[EthereumDerivationPath] = ethereumDerivationPath
    }

    /** What 3.7.4 stored under `<id>:ACCESS_SECRETS` and in the `meta_accounts` row. */
    class LegacyWallet(
        val accessSecretsHex: String,
        val substrateKeypair: Keypair,
        val substratePublicKey: ByteArray,
        val substrateAccountId: ByteArray,
        val substrateCryptoType: CryptoType,
        val ethereumKeypair: Keypair?,
        val ethereumPublicKey: ByteArray?,
        val ethereumAddress: ByteArray?
    )

    /**
     * 2.0.2-3.7.4 Import > Raw seed. With [ethSeed] null (the user declined
     * the Ethereum account) the default Ethereum path is still stored, with
     * no Ethereum keypair and no Ethereum identity in the row.
     */
    fun importFromSeed374(
        seed: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        ethSeed: String?
    ): LegacyWallet {
        val substrateSeedBytes = seed.removePrefix("0x").fromHex()
        val ethSeedBytes = ethSeed?.let { it.removePrefix("0x").fromHex() }

        val derivationPathOrNull = derivationPath.nullIfEmpty()
        val decodedDerivationPath = derivationPathOrNull?.let {
            SubstrateJunctionDecoder.decode(it)
        }

        val keys = SubstrateKeypairFactory.generate(
            mapCryptoTypeToEncryption(selectedEncryptionType),
            substrateSeedBytes,
            decodedDerivationPath?.junctions.orEmpty()
        )

        val ethereumKeypair =
            ethSeedBytes?.let { EthereumKeypairFactory.createWithPrivateKey(it) }

        val secretsV2 = MetaAccountSecrets(
            substrateKeyPair = keys,
            substrateDerivationPath = derivationPath,
            seed = substrateSeedBytes,
            ethereumDerivationPath = DEFAULT_DERIVATION_PATH,
            ethereumKeypair = ethereumKeypair
        )

        return LegacyWallet(
            accessSecretsHex = secretsV2.toHexString(),
            substrateKeypair = keys,
            substratePublicKey = keys.publicKey,
            substrateAccountId = keys.publicKey.substrateAccountId(),
            substrateCryptoType = selectedEncryptionType,
            ethereumKeypair = ethereumKeypair,
            ethereumPublicKey = ethereumKeypair?.publicKey,
            ethereumAddress = ethereumKeypair?.publicKey?.ethereumAddressFromPublicKey()
        )
    }

    /** What 4.2.0 stored under `<id>:ETHEREUM_SECRETS` and wrote into the row. */
    class AddedEthereumAccount(
        val ethereumSecretsHex: String,
        val ethereumKeypair: Keypair,
        val ethereumPublicKey: ByteArray,
        val ethereumAddress: ByteArray
    )

    /**
     * 4.0.1-4.2.0 "add an Ethereum account" to an existing wallet. On a
     * pre-4.0 seed or JSON wallet the Substrate key stays in the legacy
     * ACCESS_SECRETS payload, which has no Ethereum keypair.
     */
    fun importAdditionalAccount420(ethSeed: String): AddedEthereumAccount {
        val ethSeedBytes = ethSeed.removePrefix("0x").fromHex()
        val ethereumKeypair = EthereumKeypairFactory.createWithPrivateKey(ethSeedBytes)
        val ethereumSecrets = EthereumSecrets(
            ethereumKeypair = ethereumKeypair,
            seed = ethSeedBytes,
            ethereumDerivationPath = DEFAULT_DERIVATION_PATH
        )
        return AddedEthereumAccount(
            ethereumSecretsHex = ethereumSecrets.toHexString(),
            ethereumKeypair = ethereumKeypair,
            ethereumPublicKey = ethereumKeypair.publicKey,
            ethereumAddress = ethereumKeypair.publicKey.ethereumAddressFromPublicKey()
        )
    }

    /**
     * The secret half of the 4.0.1-4.2.0 TonMigration (71 -> 72): a legacy
     * payload with entropy moves to `<id>:SUBSTRATE_SECRETS` (and
     * `<id>:ETHEREUM_SECRETS` when it has an Ethereum keypair) and the legacy
     * `<id>:ACCESS_SECRETS` is removed. A payload without entropy (raw seed,
     * JSON) is left exactly where it was.
     */
    fun separateSecretsTon420(preferences: EncryptedPreferences, metaIds: List<Long>) {
        metaIds.forEach { metaId ->
            val oldSecrets = preferences.getDecryptedString("$metaId:ACCESS_SECRETS")
                ?.let(MetaAccountSecrets::read) ?: return@forEach

            val entropy = oldSecrets[MetaAccountSecrets.Entropy] ?: return@forEach

            val substrateKeypair =
                oldSecrets[MetaAccountSecrets.SubstrateKeypair].let { struct ->
                    ShippedKeypair(
                        publicKey = struct[KeyPairSchema.PublicKey],
                        privateKey = struct[KeyPairSchema.PrivateKey],
                        nonce = struct[KeyPairSchema.Nonce]
                    )
                }
            val seed = oldSecrets[MetaAccountSecrets.Seed]
            val substrateDerivationPath = oldSecrets[MetaAccountSecrets.SubstrateDerivationPath]
            val ethereumKeypair = oldSecrets[MetaAccountSecrets.EthereumKeypair]?.let { struct ->
                ShippedKeypair(
                    publicKey = struct[KeyPairSchema.PublicKey],
                    privateKey = struct[KeyPairSchema.PrivateKey],
                    nonce = struct[KeyPairSchema.Nonce]
                )
            }

            val newSubstrateSecrets = SubstrateSecrets(
                substrateKeyPair = substrateKeypair,
                entropy = entropy,
                seed = seed,
                substrateDerivationPath = substrateDerivationPath
            )
            preferences.putEncryptedString("$metaId:SUBSTRATE_SECRETS", newSubstrateSecrets.toHexString())
            if (ethereumKeypair != null) {
                val newEthereumSecrets = EthereumSecrets(
                    entropy = entropy,
                    seed = ethereumKeypair.privateKey,
                    ethereumKeypair = ethereumKeypair,
                    ethereumDerivationPath = oldSecrets[MetaAccountSecrets.EthereumDerivationPath]
                )
                preferences.putEncryptedString("$metaId:ETHEREUM_SECRETS", newEthereumSecrets.toHexString())
            }
            preferences.removeKey("$metaId:ACCESS_SECRETS")
        }
    }
}
