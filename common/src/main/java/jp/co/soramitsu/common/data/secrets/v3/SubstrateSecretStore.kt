package jp.co.soramitsu.common.data.secrets.v3

import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.shared_utils.scale.EncodableStruct
import jp.co.soramitsu.shared_utils.scale.Schema
import jp.co.soramitsu.shared_utils.scale.byteArray
import jp.co.soramitsu.shared_utils.scale.schema
import jp.co.soramitsu.shared_utils.scale.string
import jp.co.soramitsu.shared_utils.scale.toHexString

private const val SUBSTRATE_SECRETS = "SUBSTRATE_SECRETS"

class SubstrateSecretStore(private val encryptedPreferences: EncryptedPreferences): SecretStore<SubstrateSecrets>{
    override fun put(metaId: Long, secrets: EncodableStruct<SubstrateSecrets>) {
        encryptedPreferences.putEncryptedString("$metaId:$SUBSTRATE_SECRETS", secrets.toHexString())
    }

    override fun get(metaId: Long): EncodableStruct<SubstrateSecrets>? {
        return encryptedPreferences.getDecryptedString("$metaId:$SUBSTRATE_SECRETS")?.let(SubstrateSecrets::read)
    }
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