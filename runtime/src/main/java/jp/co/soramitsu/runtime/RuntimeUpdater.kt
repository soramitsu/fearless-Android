package jp.co.soramitsu.runtime

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.runtimeVersionChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.runtime.ext.runtimeCacheName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

typealias RuntimeUpdateRetry = suspend () -> RuntimePreparationStatus

sealed class RuntimePreparationStatus : Updater.SideEffect {
    object Ok : RuntimePreparationStatus()

    object Outdated : RuntimePreparationStatus()

    class Error(val retry: RuntimeUpdateRetry) : RuntimePreparationStatus()
}

class RuntimeUpdater(
    private val runtimeConstructor: RuntimeConstructor,
    private val socketService: SocketService,
    private val accountRepository: AccountRepository,
    private val runtimePrepopulator: RuntimePrepopulator,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>
) : Updater {

    override suspend fun listenForUpdates(
        storageSubscriptionBuilder: SubscriptionBuilder
    ): Flow<RuntimePreparationStatus> {
        return socketService.subscriptionFlow(SubscribeRuntimeVersionRequest)
            .map { it.runtimeVersionChange().specVersion }
            .distinctUntilChanged()
            .map { performUpdate(it) }
            .catch { emit(errorStatus()) }
            .flowOn(Dispatchers.Default)
    }

    suspend fun initFromCache() {
        runtimeProperty.invalidate()

        runtimePrepopulator.maybePrepopulateCache()

        val result = runCatching { runtimeConstructor.constructFromCache(getCurrentNetworkName()) }

        val runtime = result.getOrNull() ?: return

        runtimeProperty.set(runtime)
    }

    // cannot use default parameter because of the bug in the compiler (https://youtrack.jetbrains.com/issue/KT-44849)
    private suspend fun performUpdate() = performUpdate(null)

    private suspend fun performUpdate(newRuntimeVersion: Int?) = try {
        runtimeProperty.invalidate()

        val result = if (newRuntimeVersion != null) {
            runtimeConstructor.constructRuntime(newRuntimeVersion, getCurrentNetworkName())
        } else {
            runtimeConstructor.constructRuntime(getCurrentNetworkName())
        }

        runtimeProperty.set(result.runtime)

        getPreparationStatus(result)
    } catch (_: Exception) {
        errorStatus()
    }

    private fun errorStatus(): RuntimePreparationStatus.Error = RuntimePreparationStatus.Error(::performUpdate)

    private suspend fun getCurrentNetworkName(): String {
        val networkType = accountRepository.getSelectedNode().networkType

        return networkType.runtimeCacheName()
    }

    private fun getPreparationStatus(constructed: RuntimeConstructor.Constructed) = if (constructed.isNewest) {
        RuntimePreparationStatus.Ok
    } else {
        RuntimePreparationStatus.Outdated
    }
}