package jp.co.soramitsu.tonconnect.api.domain

import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.tonconnect.api.model.TonConnectionIdentity
import jp.co.soramitsu.tonconnect.api.model.TonDappConnection
import kotlinx.coroutines.flow.Flow

interface TonConnectRepository {
    suspend fun reconcilePendingMutation()

    suspend fun saveConnection(connection: TonConnectionLocal, keypair: Keypair)

    fun observeConnections(metaId: Long, source: ConnectionSource): Flow<List<TonDappConnection>>

    suspend fun getConnections(metaId: Long, source: ConnectionSource): List<TonDappConnection>

    suspend fun deleteConnection(identity: TonConnectionIdentity)
    suspend fun getConnectionKeypair(identity: TonConnectionIdentity): Keypair?
    suspend fun getConnection(identity: TonConnectionIdentity): TonConnectionLocal?
}
