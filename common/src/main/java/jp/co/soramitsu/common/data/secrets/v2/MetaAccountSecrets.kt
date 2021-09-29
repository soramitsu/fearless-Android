package jp.co.soramitsu.common.data.secrets.v2

import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.string

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

object ChainAccountSecrets : Schema<ChainAccountSecrets>() {
    val Entropy by byteArray().optional()
    val Seed by byteArray().optional()

    val Keypair by schema(KeyPairSchema)
    val DerivationPath by string().optional()
}

fun MetaAccountSecrets(
    substrateKeyPair: Keypair,
    entropy: ByteArray? = null,
    seed: ByteArray? = null,
    substrateDerivationPath: String? = null,
    ethereumKeypair: Keypair? = null,
    ethereumDerivationPath: String? = null,
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

fun ChainAccountSecrets(
    keyPair: Keypair,
    entropy: ByteArray? = null,
    seed: ByteArray? = null,
    derivationPath: String? = null,
): EncodableStruct<ChainAccountSecrets> = ChainAccountSecrets { secrets ->
    secrets[Entropy] = entropy
    secrets[Seed] = seed

    secrets[Keypair] = KeyPairSchema { keypair ->
        keypair[PublicKey] = keyPair.publicKey
        keypair[PrivateKey] = keyPair.privateKey
        keypair[Nonce] = (keyPair as? Sr25519Keypair)?.nonce
    }
    secrets[DerivationPath] = derivationPath
}
