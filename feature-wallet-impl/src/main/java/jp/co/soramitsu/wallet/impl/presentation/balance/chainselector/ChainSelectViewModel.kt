package jp.co.soramitsu.wallet.impl.presentation.balance.chainselector

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.common.base.BaseViewModel
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
import javax.inject.Inject

@HiltViewModel
class ChainSelectViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val chainInteractor: ChainInteractor,
    savedStateHandle: SavedStateHandle,
    private val sharedSendState: SendSharedState,
    private val accountInteractor: AccountInteractor
) : BaseViewModel() {

    private val initialSelectedChainId: ChainId? = savedStateHandle[ChainSelectFragment.KEY_SELECTED_CHAIN_ID]
    private val selectedChainId = MutableStateFlow(initialSelectedChainId)

    private val initialSelectedAssetId: String? = savedStateHandle[ChainSelectFragment.KEY_SELECTED_ASSET_ID]
    private val filterChainIds: List<ChainId>? = savedStateHandle[ChainSelectFragment.KEY_FILTER_CHAIN_IDS]
    private val chooserMode: Boolean = savedStateHandle[ChainSelectFragment.KEY_CHOOSER_MODE] ?: false
    private val showAllChains: Boolean = savedStateHandle[ChainSelectFragment.KEY_SHOW_ALL_CHAINS] ?: true
    private val tokenCurrencyId: String? = savedStateHandle[ChainSelectFragment.KEY_CURRENCY_ID]

    private var choiceDone = false

    private val chainsFlow = chainInteractor.getChainsFlow().mapNotNull { chains ->
        when {
            initialSelectedAssetId != null -> {
                chains.firstOrNull {
                    initialSelectedChainId in listOf(null, it.id) && it.assets.any { it.id == initialSelectedAssetId }
                }?.let { chainOfTheAsset ->
                    selectedChainId.value = chainOfTheAsset.id

                    val symbolToShow = chainOfTheAsset.assets.firstOrNull { it.id == (initialSelectedAssetId) }?.symbolToShow
                    val chainsWithAsset = chains.filter { it.assets.any { it.symbolToShow == symbolToShow } }
                    chainsWithAsset
                }
            }
            filterChainIds.isNullOrEmpty() -> {
                chains
            }
            else -> {
                chains.filter { it.id in filterChainIds }
            }
        }
    }.map { chains ->
        val meta = accountInteractor.selectedMetaAccount()
        val ethBasedChainAccounts = meta.chainAccounts.filter { it.value.chain?.isEthereumBased == true }
        val ethBasedChains = chains.filter { it.isEthereumBased }
        val filtered = if (meta.ethereumPublicKey == null && ethBasedChains.size != ethBasedChainAccounts.size) {
            val ethChainsWithNoAccounts = ethBasedChains.filter { it.id !in ethBasedChainAccounts.keys }
            chains.filter { it !in ethChainsWithNoAccounts }
        } else {
            chains
        }
        filtered.map { it.toChainItemState() }
    }

    private val symbolFlow = chainInteractor.getChainsFlow().map { chains ->
        (initialSelectedAssetId ?: sharedSendState.assetId)?.let {
            chains.firstOrNull { it.assets.any { it.id == initialSelectedAssetId } }?.let { chainOfTheAsset ->
                selectedChainId.value = chainOfTheAsset.id

                val symbol = chainOfTheAsset.assets.firstOrNull { it.id == (initialSelectedAssetId) }?.symbolToShow
                symbol
            }
        }
    }.stateIn(this, SharingStarted.Eagerly, null)

    private val enteredChainQueryFlow = MutableStateFlow("")

    val state = combine(chainsFlow, selectedChainId, enteredChainQueryFlow) { chainItems, selectedChainId, searchQuery ->
        val chains = chainItems
            .filter {
                val condition = it.tokenSymbols.values.any { it.contains(searchQuery, true) }

                searchQuery.isEmpty() || it.title.contains(searchQuery, true) || condition
            }
            .sortedWith(compareBy<ChainItemState> { it.id.defaultChainSort() }.thenBy { it.title })

        ChainSelectScreenViewState(
            chains = chains,
            selectedChainId = selectedChainId,
            searchQuery = searchQuery,
            showAllChains = showAllChains
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChainSelectScreenViewState.default)

    fun onChainSelected(chainItemState: ChainItemState?) {
        choiceDone = true
        val chainId = chainItemState?.id
        if (selectedChainId.value == chainId) {
            if (chooserMode) {
                walletRouter.back()
            }
            return
        }

        selectedChainId.value = chainId
        walletRouter.setChainSelectorPayload(chainId)
        if (chooserMode) {
            walletRouter.back()
            return
        }

        val assetId = chainItemState?.tokenSymbols?.entries?.firstOrNull { it.value == symbolFlow.value }?.key

        chainId?.let {
            launch {
                val chain = walletInteractor.getChain(it)
                if (sharedSendState.assetId == null) {
                    when {
                        chain.assets.size == 1 -> {
                            assetSpecified(assetId = chain.assets[0].id, chainId = chain.id)
                        }
                        chain.assets.filter { it.currencyId == tokenCurrencyId }.size == 1 -> {
                            assetSpecified(assetId = chain.assets.filter { it.currencyId == tokenCurrencyId }[0].id, chainId = chain.id)
                        }
                        else -> {
                            walletRouter.back()
                            walletRouter.openSelectChainAsset(chain.id)
                        }
                    }
                } else {
                    assetSpecified(assetId = assetId ?: chain.utilityAsset.id, chainId = it)
                }
            }
        }
    }

    private fun assetSpecified(assetId: String, chainId: ChainId) {
        choiceDone = true
        sharedSendState.update(assetId = assetId, chainId = chainId)
        walletRouter.back()
    }

    fun onSearchInput(input: String) {
        enteredChainQueryFlow.value = input
    }

    fun onDialogClose() {
        if (!choiceDone && sharedSendState.assetId == null) {
            walletRouter.popOutOfSend()
        }
    }
}
