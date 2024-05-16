package jp.co.soramitsu.runtime.multiNetwork.runtime

import android.util.Log
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.ChainState
import jp.co.soramitsu.runtime.multiNetwork.ChainsStateTracker
import jp.co.soramitsu.shared_utils.wsrpc.executeAsync
import jp.co.soramitsu.shared_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.shared_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.chain.StateRuntimeVersionRequest
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.chain.SubscribeRuntimeVersionRequest
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.chain.SubscribeStateRuntimeVersionRequest
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.chain.runtimeVersionChange
import jp.co.soramitsu.shared_utils.wsrpc.subscriptionFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class RuntimeVersionSubscription(
    private val chainId: String,
    connection: ChainConnection,
    private val chainDao: ChainDao,
    private val runtimeSyncService: RuntimeSyncService,
    private val networkStateService: NetworkStateService,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    init {
        runCatching {
            ChainsStateTracker.updateState(chainId) { it.copy(runtimeVersion = ChainState.Status.Started) }

            scope.launch {
                // await connection
                connection.isConnected.first()
                connection.socketService.subscriptionFlow(SubscribeRuntimeVersionRequest)
                    .map { it.runtimeVersionChange().specVersion }
                    .catch {
                        emitAll(
                            connection.socketService.subscriptionFlow(
                                SubscribeStateRuntimeVersionRequest
                            )
                                .map { it.runtimeVersionChange().specVersion }
                                .catch {

                                    val version = connection.getVersionChainRpc()
                                        ?: connection.getVersionStateRpc()
                                        ?: error("Runtime version not obtained")

                                    emit(version)
                                }
                        )
                    }
                    .onEach { runtimeVersionResult ->
                        chainDao.updateRemoteRuntimeVersion(
                            chainId,
                            runtimeVersionResult
                        )

                        runtimeSyncService.applyRuntimeVersion(chainId)

                        ChainsStateTracker.updateState(chainId) { it.copy(runtimeVersion = ChainState.Status.Completed) }
                    }
                    .catch { error ->
                        networkStateService.notifyChainSyncProblem(chainId)
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

    private suspend fun ChainConnection.getVersionChainRpc(): Int? = runCatching {
        socketService.executeAsync(
            request = RuntimeVersionRequest(),
            mapper = pojo<RuntimeVersion>().nonNull()
        ).specVersion
    }.getOrNull()

    private suspend fun ChainConnection.getVersionStateRpc(): Int? = runCatching {
        socketService.executeAsync(
            request = StateRuntimeVersionRequest(),
            mapper = pojo<RuntimeVersion>().nonNull()
        ).specVersion
    }.getOrNull()

    fun cancel() {
        scope.coroutineContext.cancel()
    }
}