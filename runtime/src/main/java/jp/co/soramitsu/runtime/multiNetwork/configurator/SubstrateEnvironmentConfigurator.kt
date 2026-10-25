package jp.co.soramitsu.runtime.multiNetwork.configurator

import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService

class SubstrateEnvironmentConfigurator(
    private val connectionPool: ConnectionPool,
    private val runtimeProviderPool: RuntimeProviderPool,
    private val runtimeSyncService: RuntimeSyncService,
    private val runtimeSubscriptionPool: RuntimeSubscriptionPool
) : ChainEnvironmentConfigurator {


    override suspend fun configure(chain: Chain) {
        require(chain.ecosystem == Ecosystem.Substrate || chain.ecosystem == Ecosystem.EthereumBased)

        // Always reread/reconcile before an existing runtime-provider early return.
        val connection = connectionPool.setupConnection(chain)
        val fresh = connection.chain as Chain
        if (runtimeProviderPool.getRuntimeProviderOrNull(fresh.id)?.getOrNull() != null) return

        runtimeSubscriptionPool.setupRuntimeSubscription(fresh, connection)
        runtimeSyncService.registerChain(fresh)
        runtimeProviderPool.setupRuntimeProvider(fresh)
    }
}

