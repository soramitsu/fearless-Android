package jp.co.soramitsu.feature_account_impl.data

import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class SecretStore(
    private val encryptedPreferences: EncryptedPreferences,
) {

    suspend fun putMetaAccountSecrets(metaId: Long, secrets: EncodableStruct<Secrets>) = withContext(Dispatchers.IO) {
        encryptedPreferences.putEncryptedString(metaAccountKey(metaId), secrets.toHexString())
    }

    suspend fun getMetaAccountSecrets(metaId: Long): EncodableStruct<Secrets>? = withContext(Dispatchers.IO) {
        encryptedPreferences.getDecryptedString(metaAccountKey(metaId))?.let(Secrets::read)
    }

    suspend fun putChainAccountSecrets(metaId: Long, chainId: String, secrets: EncodableStruct<Secrets>) = withContext(Dispatchers.IO) {
        encryptedPreferences.putEncryptedString(chainAccountKey(metaId, chainId), secrets.toHexString())
    }

    suspend fun getChainAccountSecrets(metaId: Long, chainId: String): EncodableStruct<Secrets>? = withContext(Dispatchers.IO) {
        encryptedPreferences.getDecryptedString(chainAccountKey(metaId, chainId))?.let(Secrets::read)
    }

    private fun chainAccountKey(metaId: Long, chainId: String) = metaAccountKey(metaId) + chainId

    private fun metaAccountKey(metaId: Long) = metaId.toString()
}
