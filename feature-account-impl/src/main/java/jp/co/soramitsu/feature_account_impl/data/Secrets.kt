package jp.co.soramitsu.feature_account_impl.data

import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.string

object Secrets : Schema<Secrets>() {
    val Entropy by byteArray().optional()
    val Seed by byteArray().optional()

    val SubstrateKeypair by schema(KeyPair)
    val SubstrateDerivationPath by string().optional()

    val EthereumKeypair by schema(KeyPair).optional()
    val EthereumDerivationPath by string().optional()

    object KeyPair : Schema<KeyPair>() {
        val PrivateKey by byteArray()
        val PublicKey by byteArray()

        val Nonce by byteArray().optional()
    }
}

fun Secrets(
    substrateKeyPair: Keypair,
    entropy: ByteArray? = null,
    seed: ByteArray? = null,
    substrateDerivationPath: String? = null,
    ethereumKeypair: Keypair? = null,
    ethereumDerivationPath: String? = null,
): EncodableStruct<Secrets> = Secrets { secrets ->
    secrets[Entropy] = entropy
    secrets[Seed] = seed

    secrets[SubstrateKeypair] = Secrets.KeyPair { keypair ->
        keypair[PublicKey] = substrateKeyPair.publicKey
        keypair[PrivateKey] = substrateKeyPair.privateKey
        keypair[Nonce] = substrateKeyPair.nonce
    }
    secrets[SubstrateDerivationPath] = substrateDerivationPath

    secrets[EthereumKeypair] = ethereumKeypair?.let {
        Secrets.KeyPair { keypair ->
            keypair[PublicKey] = it.publicKey
            keypair[PrivateKey] = it.privateKey
            keypair[Nonce] = it.nonce
        }
    }
    secrets[EthereumDerivationPath] = ethereumDerivationPath
}
