package jp.co.soramitsu.tonconnect.impl.data

import android.util.Log
import co.jp.soramitsu.tonconnect.domain.TonConnectRepository
import co.jp.soramitsu.tonconnect.model.TonDappConnection
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.coredb.dao.TonConnectDao
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.scale.toHexString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TonConnectRepositoryImpl(
    private val tonConnectDao: TonConnectDao,
    private val encryptedPreferences: EncryptedPreferences
) : TonConnectRepository {

    companion object {
        private const val TON_CONNECT_KEYPAIR_PREFIX = "TON_CONNECT"
    }

    override suspend fun saveConnection(connection: TonConnectionLocal, keypair: Keypair) {
        val schema = KeyPairSchema { kp ->
            kp[PublicKey] = keypair.publicKey
            kp[PrivateKey] = keypair.privateKey
            kp[Nonce]
        }
        Log.d("&&&", "saving new connection, clientId: ${connection.clientId}")
        encryptedPreferences.putEncryptedString("${TON_CONNECT_KEYPAIR_PREFIX}_${connection.clientId}", schema.toHexString())
        tonConnectDao.insertTonConnection(connection)
    }

    override fun getConnectionKeypair(clientId: String): Keypair? {
        Log.d("&&&", "reading keypair for clientId $clientId")
        val decryptedString = encryptedPreferences.getDecryptedString("${TON_CONNECT_KEYPAIR_PREFIX}_${clientId}") ?: return null

        val schema = KeyPairSchema.read(decryptedString)
        val kp =  Keypair(schema[KeyPairSchema.PublicKey], schema[KeyPairSchema.PrivateKey])
        Log.d("&&&", "got keypair for clientId ${clientId}, pubkey is ${kp.publicKey.toHexString(true)}")
        return kp
    }

    override fun observeConnections(): Flow<List<TonDappConnection>> {
        return tonConnectDao.observeTonConnections().map { it.map { localModel -> TonDappConnection(localModel) } }
    }

    override suspend fun deleteConnection(dappId: String) {
        tonConnectDao.deleteTonConnection(dappId)
    }
}