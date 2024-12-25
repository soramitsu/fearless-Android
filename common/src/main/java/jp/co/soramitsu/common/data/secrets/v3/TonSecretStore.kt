package jp.co.soramitsu.common.data.secrets.v3

import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.shared_utils.scale.EncodableStruct
import jp.co.soramitsu.shared_utils.scale.Schema
import jp.co.soramitsu.shared_utils.scale.byteArray
import jp.co.soramitsu.shared_utils.scale.toHexString

private const val TON_SECRETS = "TON_SECRETS"

class TonSecretStore(private val encryptedPreferences: EncryptedPreferences): SecretStore<TonSecrets>{
    override fun put(metaId: Long, secrets: EncodableStruct<TonSecrets>) {
        encryptedPreferences.putEncryptedString("$metaId:$TON_SECRETS", secrets.toHexString())
    }

    override fun get(metaId: Long): EncodableStruct<TonSecrets>? {
        return encryptedPreferences.getDecryptedString("$metaId:$TON_SECRETS")
            ?.let(TonSecrets::read)
    }
}

object TonSecrets : Schema<TonSecrets>() {
    val PrivateKey by byteArray()
    val PublicKey by byteArray()
}

fun TonSecrets(tonKeypair: Keypair): EncodableStruct<TonSecrets> = TonSecrets { secrets ->
    secrets[PrivateKey] = tonKeypair.privateKey
    secrets[PublicKey] = tonKeypair.publicKey
}