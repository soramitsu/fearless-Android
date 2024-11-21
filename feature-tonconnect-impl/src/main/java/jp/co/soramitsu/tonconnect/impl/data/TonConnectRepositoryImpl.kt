package jp.co.soramitsu.tonconnect.impl.data

import co.jp.soramitsu.tonconnect.domain.TonConnectRepository
import jp.co.soramitsu.coredb.dao.TonConnectDao
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import kotlinx.coroutines.flow.Flow

class TonConnectRepositoryImpl(
    private val tonConnectDao: TonConnectDao
) : TonConnectRepository {

    override suspend fun saveConnection(connection: TonConnectionLocal) {
        println("!!! saveConnection: ${connection.clientId} : ${connection.url}")
        tonConnectDao.insertTonConnection(connection)
    }

    override fun observeConnections(): Flow<List<TonConnectionLocal>> {
        return tonConnectDao.observeTonConnections()
    }

    override suspend fun deleteConnection(dappId: String) {
        tonConnectDao.deleteTonConnection(dappId)
    }
}