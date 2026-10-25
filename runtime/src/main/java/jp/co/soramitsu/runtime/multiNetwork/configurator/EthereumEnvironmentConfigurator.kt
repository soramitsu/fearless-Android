package jp.co.soramitsu.runtime.multiNetwork.configurator

import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool

/**
 * Sets up connections for EVM-compatible chains and reports node switches.
 */
class EthereumEnvironmentConfigurator(
    private val ethereumConnectionPool: EthereumConnectionPool
    ) : ChainEnvironmentConfigurator {

    override suspend fun configure(chain: Chain) {
        require(chain.ecosystem == Ecosystem.Ethereum)
        ethereumConnectionPool.setupConnection(chain) { _, _ ->
            // Automatic transport failover is observational; it cannot rewrite user selection.
        }
    }
}
