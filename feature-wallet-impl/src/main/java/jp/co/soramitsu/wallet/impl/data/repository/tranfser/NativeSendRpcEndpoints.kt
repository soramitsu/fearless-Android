package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.common.data.network.solana.SolanaRpcRoutes
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.runtime.ext.universalWalletSolanaIndexerNetwork
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

/** Resolves only registry peers for the same cluster, with a second known public mainnet provider. */
object NativeSendRpcEndpoints {
    fun resolve(chain: Chain): List<String> {
        val network = chain.universalWalletSolanaIndexerNetwork()
            ?: throw NativeSendException(NativeSendException.Code.UNSUPPORTED_ACCOUNT)
        val selected = requireNotNull(chain.nodes.singleOrNull { it.isActive }) { "Selected endpoint unavailable" }
        val preferred = SolanaRpcRoutes.normalizeRpcUrl(selected.url)
        val alternatives = chain.nodes.asSequence().filterNot { it.isActive }.sortedByDescending { it.isDefault }
            .mapNotNull { runCatching { SolanaRpcRoutes.normalizeRpcUrl(it.url) }.getOrNull() }
        val configured = (sequenceOf(preferred) + alternatives).distinct().take(8).toList()
        val public = listOf(UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL, "https://solana-rpc.publicnode.com")
        return if (network.rpcUrl == public.first() && configured.all { it in public }) {
            (configured + public).distinct()
        } else {
            configured
        }
    }
}
