package jp.co.soramitsu.common.data.secrets.v3

import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.readValidatedWalletSecretOrQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.readWalletSecretOrQuarantine
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.string
import jp.co.soramitsu.fearless_utils.scale.toHexString

private const val SUBSTRATE_SECRETS = "SUBSTRATE_SECRETS"

class SubstrateSecretStore internal constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val walletRootSecretValidation: WalletRootSecretValidation
) : SecretStore<SubstrateSecrets> {

    constructor(encryptedPreferences: EncryptedPreferences) : this(
        encryptedPreferences = encryptedPreferences,
        walletRootSecretValidation = WalletRootSecretValidator
    )

    override fun put(metaId: Long, secrets: EncodableStruct<SubstrateSecrets>) {
        encryptedPreferences.putEncryptedString("$metaId:$SUBSTRATE_SECRETS", secrets.toHexString())
    }

    @Deprecated(
        message = "Unvalidated access is reserved for historical migrations; runtime callers must bind the durable identity"
    )
    override fun get(metaId: Long): EncodableStruct<SubstrateSecrets>? {
        return encryptedPreferences.readWalletSecretOrQuarantine(
            activeSecretKey = activeKey(metaId),
            decode = { encoded ->
                WalletSecretScalePreflight.requireSubstrateV3(encoded)
                SubstrateSecrets.read(encoded)
            }
        )
    }

    fun get(
        metaId: Long,
        expectedPublicKey: ByteArray?,
        expectedCryptoType: CryptoType?,
        expectedAccountId: ByteArray?
    ): EncodableStruct<SubstrateSecrets>? {
        return encryptedPreferences.readValidatedWalletSecretOrQuarantine(
            activeSecretKey = activeKey(metaId),
            isLocalCorruption = { it is WalletRootSecretCorruptionException }
        ) { encoded ->
            val publicKey = expectedPublicKey ?: throw WalletPublicIdentityIntegrityException(
                "An active Substrate secret has no durable public key"
            )
            val cryptoType = expectedCryptoType ?: throw WalletPublicIdentityIntegrityException(
                "An active Substrate secret has no durable crypto type"
            )
            val accountId = expectedAccountId ?: throw WalletPublicIdentityIntegrityException(
                "An active Substrate secret has no durable account id"
            )
            val canonical = walletRootSecretValidation
                .validateSubstrateAndSanitize(
                    encoded = encoded,
                    expectedPublicKey = publicKey,
                    expectedCryptoType = cryptoType,
                    expectedAccountId = accountId
            )
            WalletSecretScalePreflight.requireSubstrateV3(canonical)
            SubstrateSecrets.read(canonical)
        }
    }

    private fun activeKey(metaId: Long) = "$metaId:$SUBSTRATE_SECRETS"
}

object SubstrateSecrets : Schema<SubstrateSecrets>() {
    val Entropy by byteArray().optional()
    val Seed by byteArray().optional()

    val SubstrateKeypair by schema(KeyPairSchema)
    val SubstrateDerivationPath by string().optional()
}

fun SubstrateSecrets(substrateKeyPair: Keypair,
                     entropy: ByteArray? = null,
                     seed: ByteArray? = null,
                     substrateDerivationPath: String? = null): EncodableStruct<SubstrateSecrets> = SubstrateSecrets { secrets ->
    secrets[Entropy] = entropy
    secrets[Seed] = seed

    secrets[SubstrateKeypair] = KeyPairSchema { keypair ->
        keypair[PublicKey] = substrateKeyPair.publicKey
        keypair[PrivateKey] = substrateKeyPair.privateKey
        keypair[Nonce] = (substrateKeyPair as? Sr25519Keypair)?.nonce
    }
    secrets[SubstrateDerivationPath] = substrateDerivationPath
}
