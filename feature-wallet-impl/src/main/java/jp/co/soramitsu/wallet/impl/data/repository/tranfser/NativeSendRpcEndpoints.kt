package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.solana.SolanaRpcEndpoints
import jp.co.soramitsu.runtime.ext.universalWalletSolanaIndexerNetwork
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

/**
 * Ordered JSON-RPC endpoints for balances, quotes, broadcast and status checks of one Solana chain:
 * the optional build provider, the selected node, the public mainnet nodes (mainnet only), then the
 * chain's other nodes. Requests remain bound to the chain's genesis hash by the RPC client.
 */
object NativeSendRpcEndpoints {
    fun resolve(chain: Chain, buildProvider: String? = BuildConfig.SOLANA_RPC_PROVIDER_URL): List<String> {
        val network = chain.universalWalletSolanaIndexerNetwork()
            ?: throw NativeSendException(NativeSendException.Code.UNSUPPORTED_ACCOUNT)
        val selected = requireNotNull(chain.nodes.singleOrNull { it.isActive }) { "Selected endpoint unavailable" }
        val others = chain.nodes.filterNot { it.isActive }.sortedByDescending { it.isDefault }.map { it.url }
        return SolanaRpcEndpoints.ordered(network.genesisHash, selected.url, others, buildProvider)
    }
}
