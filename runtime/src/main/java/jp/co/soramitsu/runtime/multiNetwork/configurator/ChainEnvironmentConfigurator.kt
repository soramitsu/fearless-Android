package jp.co.soramitsu.runtime.multiNetwork.configurator

import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService

interface ChainEnvironmentConfigurator {
    suspend fun configure(chain: Chain)
}

class ChainEnvironmentConfiguratorProvider(
    private val connectionPool: ConnectionPool,
    private val runtimeProviderPool: RuntimeProviderPool,
    private val runtimeSyncService: RuntimeSyncService,
    private val runtimeSubscriptionPool: RuntimeSubscriptionPool,
    private val chainsRepository: ChainsRepository,
    private val ethereumConnectionPool: EthereumConnectionPool
) {
    fun provide(chain: Chain): ChainEnvironmentConfigurator {
        return when (chain.ecosystem) {
           Ecosystem.Substrate,
           Ecosystem.EthereumBased -> SubstrateEnvironmentConfigurator(
                connectionPool,
                runtimeProviderPool,
                runtimeSyncService,
                runtimeSubscriptionPool,
                chainsRepository
            )

            Ecosystem.Ethereum -> EthereumEnvironmentConfigurator(
                ethereumConnectionPool,
                chainsRepository
            )

            Ecosystem.Ton -> TonEnvironmentConfigurator()
        }
    }
}