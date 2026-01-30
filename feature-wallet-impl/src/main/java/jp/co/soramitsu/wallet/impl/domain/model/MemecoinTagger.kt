package jp.co.soramitsu.wallet.impl.domain.model

import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.solana.SolanaChainDefinition

object MemecoinTagger {

    fun isMemecoin(chainId: ChainId, chainAssetId: String): Boolean {
        if (chainId != SolanaChainDefinition.CHAIN_ID) return false

        return chainAssetId in SolanaChainDefinition.memecoinAssetIds
    }
}
