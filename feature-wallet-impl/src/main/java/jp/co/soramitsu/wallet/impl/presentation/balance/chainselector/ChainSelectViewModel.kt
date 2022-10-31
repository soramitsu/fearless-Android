package jp.co.soramitsu.wallet.impl.presentation.balance.chainselector

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.defaultChainSort
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ChainSelectViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val chainInteractor: ChainInteractor,
    savedStateHandle: SavedStateHandle,
    private val sharedSendState: SendSharedState
) : BaseViewModel() {

    private val initialSelectedChainId: ChainId? = savedStateHandle[ChainSelectFragment.KEY_SELECTED_CHAIN_ID]
    private val selectedChainId = MutableStateFlow(initialSelectedChainId)

    private val initialSelectedAssetId: String? = savedStateHandle[ChainSelectFragment.KEY_NARROW_BY_ASSET_ID]

    private val chainsFlow = chainInteractor.getChainsFlow().mapNotNull { chains ->
        if (initialSelectedAssetId != null) {
            chains.firstOrNull { it.assets.any { it.id == initialSelectedAssetId } }?.let { chainOfTheAsset ->
                selectedChainId.value = chainOfTheAsset.chain.id

                val symbol = chainOfTheAsset.assets.firstOrNull { it.id == (initialSelectedAssetId) }?.symbolToShow

                val chainsWithAsset = chains.filter { it.assets.any { it.symbolToShow == symbol } }
                chainsWithAsset
            }
        } else {
            chains
        }
    }.map { chains: List<JoinedChainInfo> ->
        chains.map { it.toChainItemState() }
    }

    private val symbolFlow = chainInteractor.getChainsFlow().map { chains ->
        initialSelectedAssetId?.let {
            chains.firstOrNull { it.assets.any { it.id == initialSelectedAssetId } }?.let { chainOfTheAsset ->
                selectedChainId.value = chainOfTheAsset.chain.id

                val symbol = chainOfTheAsset.assets.firstOrNull { it.id == (initialSelectedAssetId) }?.symbolToShow
                symbol
            }
        }
    }.stateIn(this, SharingStarted.Eagerly, null)

    private val enteredChainQueryFlow = MutableStateFlow("")

    val state = combine(chainsFlow, selectedChainId, enteredChainQueryFlow) { chainItems, selectedChainId, searchQuery ->
        val chains = chainItems
            .filter {
                searchQuery.isEmpty() || it.title.contains(searchQuery, true) || it.tokenSymbols.any { it.second.contains(searchQuery, true) }
            }
            .sortedWith(compareBy<ChainItemState> { it.id.defaultChainSort() }.thenBy { it.title })

        ChainSelectScreenViewState(
            chains = chains,
            selectedChainId = selectedChainId,
            searchQuery = searchQuery,
            showAllChains = false
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChainSelectScreenViewState.default)

    fun onChainSelected(item: ChainItemState? = null) {
        if (selectedChainId.value != item?.id) {
            selectedChainId.value = item?.id
            val assetId = item?.tokenSymbols?.firstOrNull { it.second == symbolFlow.value }?.first

            item?.id?.let {
                if (assetId != null) {
                    sharedSendState.update(chainId = it, assetId = assetId)
                } else {
                    launch {
                        val chain = walletInteractor.getChain(it)
                        sharedSendState.update(chainId = it, assetId = chain.utilityAsset.id)
                    }
                }
            }
        }

        walletRouter.back()
    }

    fun onChainSearchEntered(query: String) {
        enteredChainQueryFlow.value = query
    }

    fun onBackClicked() {
        walletRouter.back()
    }
}
