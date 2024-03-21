package jp.co.soramitsu.runtime.multiNetwork.runtime

import android.util.Log
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.ChainState
import jp.co.soramitsu.runtime.multiNetwork.ChainsStateTracker
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.chain.SubscribeRuntimeVersionRequest
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.chain.runtimeVersionChange
import jp.co.soramitsu.shared_utils.wsrpc.state.SocketStateMachine
import jp.co.soramitsu.shared_utils.wsrpc.subscriptionFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class RuntimeVersionSubscription(
    private val chainId: String,
    connection: ChainConnection,
    private val chainDao: ChainDao,
    private val runtimeSyncService: RuntimeSyncService
) : CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    init {
        runCatching {
            ChainsStateTracker.updateState(chainId) { it.copy(runtimeVersion = ChainState.Status.Started) }
            launch {
                // await connection
                connection.state.first { it is SocketStateMachine.State.Connected }
                connection.socketService.subscriptionFlow(SubscribeRuntimeVersionRequest)
                    .map { it.runtimeVersionChange().specVersion }
                    .onEach { runtimeVersionResult ->
                        chainDao.updateRemoteRuntimeVersion(
                            chainId,
                            runtimeVersionResult
                        )

                        runtimeSyncService.applyRuntimeVersion(chainId)
                        ChainsStateTracker.updateState(chainId) { it.copy(runtimeVersion = ChainState.Status.Completed) }
                    }
                    .catch { error ->
                        ChainsStateTracker.updateState(chainId) {
                            it.copy(
                                runtimeVersion = ChainState.Status.Failed(
                                    error
                                )
                            )
                        }
                        Log.e(
                            "RuntimeVersionSubscription",
                            "Failed to subscribe runtime version for chain: $chainId. Error: $error"
                        )
                        error.printStackTrace()
                    }
                    .launchIn(this)
            }
        }
    }
}