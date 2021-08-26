package jp.co.soramitsu.runtime.multiNetwork.runtime

import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.runtimeVersionChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class RuntimeVersionSubscription(
    private val chainId: String,
    connection: ChainConnection,
    private val chainDao: ChainDao,
    private val runtimeSyncService: RuntimeSyncService,
) : CoroutineScope by CoroutineScope(Dispatchers.Default) {

    init {
        connection.socketService.subscriptionFlow(SubscribeRuntimeVersionRequest)
            .map { it.runtimeVersionChange().specVersion }
            .onEach { runtimeVersion ->
                chainDao.updateRemoteRuntimeVersion(chainId, runtimeVersion)

                runtimeSyncService.applyRuntimeVersion(chainId)
            }
            .launchIn(this)
    }
}
