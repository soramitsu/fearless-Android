package jp.co.soramitsu.common.data.secrets.v3

import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.shared_utils.scale.EncodableStruct
import jp.co.soramitsu.shared_utils.scale.Schema
import jp.co.soramitsu.shared_utils.scale.byteArray
import jp.co.soramitsu.shared_utils.scale.string
import jp.co.soramitsu.shared_utils.scale.toHexString

private const val SOLANA_SECRETS = "SOLANA_SECRETS"

class SolanaSecretStore(private val encryptedPreferences: EncryptedPreferences) : SecretStore<SolanaSecrets> {
    override fun put(metaId: Long, secrets: EncodableStruct<SolanaSecrets>) {
        encryptedPreferences.putEncryptedString("$metaId:$SOLANA_SECRETS", secrets.toHexString())
    }

    override fun get(metaId: Long): EncodableStruct<SolanaSecrets>? {
        return encryptedPreferences.getDecryptedString("$metaId:$SOLANA_SECRETS")
            ?.let(SolanaSecrets::read)
    }
}

object SolanaSecrets : Schema<SolanaSecrets>() {
    val Seed by byteArray()
    val PrivateKey by byteArray()
    val PublicKey by byteArray()
    val DerivationPath by string()
}

fun SolanaSecrets(
    seed: ByteArray,
    solanaKeypair: Keypair,
    derivationPath: String
): EncodableStruct<SolanaSecrets> = SolanaSecrets { secrets ->
    secrets[Seed] = seed
    secrets[PrivateKey] = solanaKeypair.privateKey
    secrets[PublicKey] = solanaKeypair.publicKey
    secrets[DerivationPath] = derivationPath
}
