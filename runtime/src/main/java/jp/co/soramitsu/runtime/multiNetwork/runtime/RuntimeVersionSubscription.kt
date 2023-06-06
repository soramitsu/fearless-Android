package jp.co.soramitsu.runtime.multiNetwork.runtime

import android.util.Log
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.chain.SubscribeRuntimeVersionRequest
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.chain.runtimeVersionChange
import jp.co.soramitsu.shared_utils.wsrpc.subscriptionFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class RuntimeVersionSubscription(
    private val chainId: String,
    connection: ChainConnection,
    private val chainDao: ChainDao,
    private val runtimeSyncService: RuntimeSyncService
) : CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    init {
        connection.socketService.subscriptionFlow(SubscribeRuntimeVersionRequest)
            .map { it.runtimeVersionChange().specVersion }
            .onEach { runtimeVersion ->
                chainDao.updateRemoteRuntimeVersion(chainId, runtimeVersion)

                runtimeSyncService.applyRuntimeVersion(chainId)
            }
            .catch {
                Log.e("RuntimeVersionSubscription", "Failed to subscribe runtime version for chain: $chainId. Error: $it")
                it.printStackTrace()
            }
            .launchIn(this)
    }
}
