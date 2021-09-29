package jp.co.soramitsu.common.data.secrets.v2

import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ACCESS_SECRETS = "ACCESS_SECRETS"

class SecretStoreV2(
    private val encryptedPreferences: EncryptedPreferences,
) {

    suspend fun putMetaAccountSecrets(metaId: Long, secrets: EncodableStruct<MetaAccountSecrets>) = withContext(Dispatchers.IO) {
        encryptedPreferences.putEncryptedString(metaAccountKey(metaId, ACCESS_SECRETS), secrets.toHexString())
    }

    suspend fun getMetaAccountSecrets(metaId: Long): EncodableStruct<MetaAccountSecrets>? = withContext(Dispatchers.IO) {
        encryptedPreferences.getDecryptedString(metaAccountKey(metaId, ACCESS_SECRETS))?.let(MetaAccountSecrets::read)
    }

    suspend fun putChainAccountSecrets(metaId: Long, accountId: ByteArray, secrets: EncodableStruct<ChainAccountSecrets>) = withContext(Dispatchers.IO) {
        encryptedPreferences.putEncryptedString(chainAccountKey(metaId, accountId, ACCESS_SECRETS), secrets.toHexString())
    }

    suspend fun getChainAccountSecrets(metaId: Long, accountId: ByteArray): EncodableStruct<ChainAccountSecrets>? = withContext(Dispatchers.IO) {
        encryptedPreferences.getDecryptedString(chainAccountKey(metaId, accountId, ACCESS_SECRETS))?.let(ChainAccountSecrets::read)
    }

    suspend fun hasChainSecrets(metaId: Long, accountId: ByteArray) = withContext(Dispatchers.Default) {
        encryptedPreferences.hasKey(chainAccountKey(metaId, accountId, ACCESS_SECRETS))
    }

    private fun chainAccountKey(metaId: Long, accountId: ByteArray, secretName: String) = "$metaId:${accountId.toHexString()}:$secretName"

    private fun metaAccountKey(metaId: Long, secretName: String) = "$metaId:$secretName"
}

suspend fun SecretStoreV2.getChainAccountKeypair(
    metaId: Long,
    accountId: ByteArray,
): Keypair = withContext(Dispatchers.Default) {
    val secrets = getChainAccountSecrets(metaId, accountId) ?: error("No secrets found for meta account $metaId for account ${accountId.toHexString()}")

    val keypairStruct = secrets[ChainAccountSecrets.Keypair]

    mapKeypairStructToKeypair(keypairStruct)
}

suspend fun SecretStoreV2.getMetaAccountKeypair(
    metaId: Long,
    isEthereum: Boolean
): Keypair = withContext(Dispatchers.Default) {
    val secrets = getMetaAccountSecrets(metaId) ?: error("No secrets found for meta account $metaId")

    val keypairStruct = if (isEthereum) {
        secrets[MetaAccountSecrets.EthereumKeypair] ?: error("No ethereum keypair found for meta account $metaId")
    } else {
        secrets[MetaAccountSecrets.SubstrateKeypair]
    }

    mapKeypairStructToKeypair(keypairStruct)
}

private fun mapKeypairStructToKeypair(struct: EncodableStruct<KeyPairSchema>): Keypair {
    return Keypair(
        publicKey = struct[KeyPairSchema.PublicKey],
        privateKey = struct[KeyPairSchema.PrivateKey],
        nonce = struct[KeyPairSchema.Nonce]
    )
}
