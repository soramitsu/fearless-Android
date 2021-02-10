package jp.co.soramitsu.app.root.data.runtime

import jp.co.soramitsu.core_api.data.network.Updater
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.runtimeVersionChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.Locale

typealias RuntimeUpdateRetry = suspend () -> RuntimePreparationStatus

sealed class RuntimePreparationStatus: Updater.SideEffect {
    object Ok : RuntimePreparationStatus()

    object Outdated: RuntimePreparationStatus()

    class Error(val retry: RuntimeUpdateRetry) : RuntimePreparationStatus()
}

class RuntimeUpdater(
    private val runtimeProvider: RuntimeProvider,
    private val socketService: SocketService,
    private val accountRepository: AccountRepository,
    private val runtimeHolder: RuntimeHolder
) : Updater {

    override suspend fun listenForUpdates(): Flow<RuntimePreparationStatus> {
        runtimeHolder.invalidate()

        return socketService.subscriptionFlow(SubscribeRuntimeVersionRequest)
            .map { it.runtimeVersionChange().specVersion }
            .distinctUntilChanged()
            .onEach { runtimeHolder.invalidate() }
            .map { runtimeProvider.prepareRuntime(it, getCurrentNetworkName()) }
            .onEach { runtimeHolder.set(it.runtime) }
            .map(::preparationStatus)
            .catch { emit(errorStatus()) }
    }

    private suspend fun manualRuntimeUpdate() = try {
        runtimeHolder.invalidate()


        val result = runtimeProvider.prepareRuntime(getCurrentNetworkName())

        runtimeHolder.set(result.runtime)

        preparationStatus(result)
    } catch (_: Exception) {
        errorStatus()
    }

    private fun errorStatus() : RuntimePreparationStatus.Error = RuntimePreparationStatus.Error(::manualRuntimeUpdate)

    private suspend fun getCurrentNetworkName() : String {
        val networkType = accountRepository.getSelectedNode().networkType

        return networkType.readableName.toLowerCase(Locale.ROOT)
    }

    private fun preparationStatus(prepared: RuntimeProvider.Prepared) = if (prepared.isNewest) {
        RuntimePreparationStatus.Ok
    } else {
        RuntimePreparationStatus.Outdated
    }
}