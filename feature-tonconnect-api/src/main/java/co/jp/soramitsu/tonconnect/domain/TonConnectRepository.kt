package co.jp.soramitsu.tonconnect.domain

import jp.co.soramitsu.coredb.model.TonConnectionLocal
import kotlinx.coroutines.flow.Flow

interface TonConnectRepository {
    suspend fun saveConnection(connection: TonConnectionLocal)

    fun observeConnections(): Flow<List<TonConnectionLocal>>
    suspend fun deleteConnection(dappId: String)
}
