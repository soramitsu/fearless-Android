package jp.co.soramitsu.tonconnect.impl.data

import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.coredb.dao.TonConnectDao
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.shared_utils.scale.toHexString
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRepository
import jp.co.soramitsu.tonconnect.api.model.TonDappConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TonConnectRepositoryImpl(
    private val tonConnectDao: TonConnectDao,
    private val encryptedPreferences: EncryptedPreferences
) : TonConnectRepository {

    override suspend fun saveConnection(connection: TonConnectionLocal, keypair: Keypair) {
        val schema = KeyPairSchema { kp ->
            kp[PublicKey] = keypair.publicKey
            kp[PrivateKey] = keypair.privateKey
            kp[Nonce]
        }

        encryptedPreferences.putEncryptedString("${TON_CONNECT_KEYPAIR_PREFIX}_${connection.clientId}", schema.toHexString())
        tonConnectDao.insertTonConnection(connection)
    }

    override fun getConnectionKeypair(clientId: String): Keypair? {
        val decryptedString = encryptedPreferences.getDecryptedString("${TON_CONNECT_KEYPAIR_PREFIX}_$clientId") ?: return null

        val schema = KeyPairSchema.read(decryptedString)
        return Keypair(schema[KeyPairSchema.PublicKey], schema[KeyPairSchema.PrivateKey])
    }

    override fun observeConnections(): Flow<List<TonDappConnection>> {
        return tonConnectDao.observeTonConnections().map { it.map { localModel -> TonDappConnection(localModel) } }
    }

    override suspend fun deleteConnection(clientId: String) {
        tonConnectDao.deleteTonConnection(clientId)
    }

    override suspend fun getConnection(metaId: Long, url: String): TonConnectionLocal? {
        val formatted = "%$url%"
        return tonConnectDao.getTonConnection(metaId, formatted)
    }

    companion object {
        private const val TON_CONNECT_KEYPAIR_PREFIX = "TON_CONNECT"
    }
}
