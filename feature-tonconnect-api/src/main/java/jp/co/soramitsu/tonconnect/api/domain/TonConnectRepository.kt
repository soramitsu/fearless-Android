package jp.co.soramitsu.tonconnect.api.domain

import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.tonconnect.api.model.TonDappConnection
import kotlinx.coroutines.flow.Flow

interface TonConnectRepository {
    suspend fun saveConnection(connection: TonConnectionLocal, keypair: Keypair)

    fun observeConnections(source: ConnectionSource): Flow<List<TonDappConnection>>
    suspend fun deleteConnection(clientId: String)
    fun getConnectionKeypair(clientId: String): Keypair?
    suspend fun getConnection(metaId: Long, url: String): TonConnectionLocal?
}
