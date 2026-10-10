package jp.co.soramitsu.common.data.secrets.v3

import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.readValidatedWalletSecretOrQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.readWalletSecretOrQuarantine
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.string
import jp.co.soramitsu.fearless_utils.scale.toHexString

private const val ETHEREUM_SECRETS = "ETHEREUM_SECRETS"

class EthereumSecretStore internal constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val walletRootSecretValidation: WalletRootSecretValidation
) : SecretStore<EthereumSecrets> {

    constructor(encryptedPreferences: EncryptedPreferences) : this(
        encryptedPreferences = encryptedPreferences,
        walletRootSecretValidation = WalletRootSecretValidator
    )

    override fun put(metaId: Long, secrets: EncodableStruct<EthereumSecrets>) {
        encryptedPreferences.putEncryptedString("$metaId:$ETHEREUM_SECRETS", secrets.toHexString())
    }

    @Deprecated(
        message = "Unvalidated access is reserved for historical migrations; runtime callers must bind the durable identity"
    )
    override fun get(metaId: Long): EncodableStruct<EthereumSecrets>? {
        return encryptedPreferences.readWalletSecretOrQuarantine(
            activeSecretKey = activeKey(metaId),
            decode = { encoded ->
                WalletSecretScalePreflight.requireEthereumV3(encoded)
                EthereumSecrets.read(encoded)
            }
        )
    }

    fun get(
        metaId: Long,
        expectedPublicKey: ByteArray?,
        expectedAddress: ByteArray?
    ): EncodableStruct<EthereumSecrets>? {
        return encryptedPreferences.readValidatedWalletSecretOrQuarantine(
            activeSecretKey = activeKey(metaId),
            isLocalCorruption = { it is WalletRootSecretCorruptionException }
        ) { encoded ->
            val publicKey = expectedPublicKey ?: throw WalletPublicIdentityIntegrityException(
                "An active Ethereum secret has no durable public key"
            )
            val address = expectedAddress ?: throw WalletPublicIdentityIntegrityException(
                "An active Ethereum secret has no durable address"
            )
            val canonical = walletRootSecretValidation
                .validateEthereumAndSanitize(
                    encoded = encoded,
                    expectedPublicKey = publicKey,
                    expectedAddress = address
            )
            WalletSecretScalePreflight.requireEthereumV3(canonical)
            EthereumSecrets.read(canonical)
        }
    }

    private fun activeKey(metaId: Long) = "$metaId:$ETHEREUM_SECRETS"
}

object EthereumSecrets : Schema<EthereumSecrets>() {
    val Entropy by byteArray().optional()
    val Seed by byteArray().optional()

    val EthereumKeypair by schema(KeyPairSchema)
    val EthereumDerivationPath by string().optional()
}

fun EthereumSecrets(entropy: ByteArray? = null,
                    seed: ByteArray? = null,
                    ethereumKeypair: Keypair,
                    ethereumDerivationPath: String? = null): EncodableStruct<EthereumSecrets> = EthereumSecrets { secrets ->
    secrets[Entropy] = entropy
    secrets[Seed] = seed
    secrets[EthereumKeypair] = ethereumKeypair.let {
        KeyPairSchema { keypair ->
            keypair[PublicKey] = it.publicKey
            keypair[PrivateKey] = it.privateKey
            keypair[Nonce] = null // ethereum does not support Sr25519 so nonce is always null
        }
    }

    secrets[EthereumDerivationPath] = ethereumDerivationPath
}
