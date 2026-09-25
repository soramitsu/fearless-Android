package jp.co.soramitsu.wallet.impl.presentation.balance.list

import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest

@OptIn(ExperimentalCoroutinesApi::class)
internal fun selectedPortfolioAddressFlow(
    selectedChainIds: Flow<ChainId?>,
    selectedWalletIds: Flow<Long>,
    resolveAddress: suspend (ChainId) -> String?
): Flow<String> = combine(selectedChainIds, selectedWalletIds) { chainId, walletId ->
    chainId to walletId
}.transformLatest { (chainId, _) ->
    // A previous wallet's address must not remain visible while its replacement resolves.
    emit("")
    if (chainId != null) emit(resolveAddress(chainId).orEmpty())
}
