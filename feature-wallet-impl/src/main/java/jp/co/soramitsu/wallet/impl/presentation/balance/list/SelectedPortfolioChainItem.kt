package jp.co.soramitsu.wallet.impl.presentation.balance.list

import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectScreenContract

internal fun selectedPortfolioChainItem(
    selectedChainId: ChainId?,
    chains: List<ChainSelectScreenContract.State.ItemState>
): ChainSelectScreenContract.State.ItemState? = if (selectedChainId == null) {
    chains.singleOrNull()
} else {
    chains.firstOrNull { it.id == selectedChainId }
}
