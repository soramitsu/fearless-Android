package jp.co.soramitsu.runtime

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.model.chainId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class RuntimeUpdater(
    private val accountRepository: AccountRepository,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val connectionProperty: SuspendableProperty<SocketService>,
    private val chainRegistry: ChainRegistry,
) {

    fun sync() {
        GlobalScope.launch {
            chainRegistry.currentChains.first { it.isNotEmpty() } // wait until chains load

            accountRepository.selectedNetworkTypeFlow()
                .onEach { runtimeProperty.invalidate() }
                .map { currentNetworkType -> chainRegistry.getService(currentNetworkType.chainId) }
                .onEach { connectionProperty.set(it.connection.socketService) }
                .flatMapLatest { it.runtimeProvider.observe() }
                .onEach(runtimeProperty::set)
                .collect()
        }
    }
}
