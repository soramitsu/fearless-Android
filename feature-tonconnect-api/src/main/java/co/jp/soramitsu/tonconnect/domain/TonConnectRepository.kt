package co.jp.soramitsu.tonconnect.domain

import co.jp.soramitsu.tonconnect.model.TonDappConnection
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import kotlinx.coroutines.flow.Flow

interface TonConnectRepository {
    suspend fun saveConnection(connection: TonConnectionLocal, keypair: Keypair)

    fun observeConnections(): Flow<List<TonDappConnection>>
    suspend fun deleteConnection(clientId: String)
    fun getConnectionKeypair(clientId: String): Keypair?
    suspend fun getConnection(metaId: Long, url: String): TonConnectionLocal?
}
