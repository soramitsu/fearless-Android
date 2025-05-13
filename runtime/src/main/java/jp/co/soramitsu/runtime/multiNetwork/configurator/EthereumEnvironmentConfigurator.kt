package jp.co.soramitsu.runtime.multiNetwork.configurator

import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EthereumEnvironmentConfigurator(
    private val ethereumConnectionPool: EthereumConnectionPool,
    private val chainsRepository: ChainsRepository
    ) : ChainEnvironmentConfigurator {
    private val scope = CoroutineScope(Dispatchers.Default)

    override suspend fun configure(chain: Chain) {
        require(chain.ecosystem == Ecosystem.Ethereum)
        ethereumConnectionPool.setupConnection(chain) { chainId, newNodeUrl ->
            scope.launch { chainsRepository.notifyNodeSwitched(chainId, newNodeUrl) }
        }
    }
}