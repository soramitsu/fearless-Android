package jp.co.soramitsu.app.root.data.runtime

import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.runtimeVersionChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

enum class RuntimePreparationStatus {
    OK, OUTDATED, ERROR
}

class RuntimeUpdater(
    private val runtimeProvider: RuntimeProvider,
    private val socketService: SocketService,
    private val runtimeHolder: RuntimeHolder

) {
    suspend fun listenRuntimeChanges(networkName: String): Flow<RuntimePreparationStatus> {

        return socketService.subscriptionFlow(SubscribeRuntimeVersionRequest)
            .map { it.runtimeVersionChange().specVersion }
            .distinctUntilChanged()
            .onEach { runtimeHolder.invalidate() }
            .map { runtimeProvider.prepareRuntime(it, networkName) }
            .onEach { runtimeHolder.set(it.runtime) }
            .map(::preparationStatus)
            .catch { emit(RuntimePreparationStatus.ERROR) }
    }

    suspend fun manualRuntimeUpdate(networkName: String) = try {
        runtimeHolder.invalidate()

        val result = runtimeProvider.prepareRuntime(networkName)

        runtimeHolder.set(result.runtime)

        preparationStatus(result)
    } catch (_: Exception) {
        RuntimePreparationStatus.ERROR
    }

    private fun preparationStatus(prepared: RuntimeProvider.Prepared) = if (prepared.isNewest) {
        RuntimePreparationStatus.OK
    } else {
        RuntimePreparationStatus.OUTDATED
    }
}