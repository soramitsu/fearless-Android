package jp.co.soramitsu.common.data.secrets.v3

import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.shared_utils.scale.EncodableStruct
import jp.co.soramitsu.shared_utils.scale.Schema
import jp.co.soramitsu.shared_utils.scale.byteArray
import jp.co.soramitsu.shared_utils.scale.schema
import jp.co.soramitsu.shared_utils.scale.string
import jp.co.soramitsu.shared_utils.scale.toHexString

private const val ETHEREUM_SECRETS = "ETHEREUM_SECRETS"

class EthereumSecretStore(private val encryptedPreferences: EncryptedPreferences): SecretStore<EthereumSecrets>{
    override fun put(metaId: Long, secrets: EncodableStruct<EthereumSecrets>) {
        encryptedPreferences.putEncryptedString("$metaId:$ETHEREUM_SECRETS", secrets.toHexString())
    }

    override fun get(metaId: Long): EncodableStruct<EthereumSecrets>? {
        return encryptedPreferences.getDecryptedString("$metaId:$ETHEREUM_SECRETS")
            ?.let(EthereumSecrets::read)
    }
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