package jp.co.soramitsu.wallet.impl.presentation.balance.chainselector

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.core.models.utilityAsset
import jp.co.soramitsu.runtime.ext.ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainEcosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.defaultChainSort
import jp.co.soramitsu.wallet.api.domain.model.XcmChainType
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import jp.co.soramitsu.wallet.api.presentation.WalletRouter as WalletRouterApi

@HiltViewModel
class ChainSelectViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    private val walletInteractor: WalletInteractor,
    chainInteractor: ChainInteractor,
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
    private val isSelectAsset: Boolean = savedStateHandle[ChainSelectFragment.KEY_SELECT_ASSET] ?: true

    // XCM
    private val xcmChainType: XcmChainType? =
        savedStateHandle[ChainSelectFragment.KEY_XCM_CHAIN_TYPE]
    private val xcmSelectedOriginalChainId: String? =
        savedStateHandle[ChainSelectFragment.KEY_XCM_SELECTED_ORIGINAL_CHAIN_ID]
    private val xcmAssetSymbol: String? =
        savedStateHandle[ChainSelectFragment.KEY_XCM_ASSET_SYMBOL]

    private var choiceDone = false

    private val allChainsFlow = if (xcmChainType == null) {
        chainInteractor.getChainsFlow()
    } else {
        combine(
            chainInteractor.getChainsFlow(),
            chainInteractor.getXcmChainIdsFlow(
                type = xcmChainType,
                originChainId = xcmSelectedOriginalChainId,
                assetSymbol = xcmAssetSymbol
            )
        ) { chains, xsmChainIds ->
            chains.filter {
                it.id in xsmChainIds
            }
        }
    }

    private val chainsFlow = allChainsFlow.map { chains ->
        when {
            initialSelectedAssetId != null -> {
                chains.firstOrNull {
                    it.assets.any { it.id == initialSelectedAssetId }
                }?.let { chainOfTheAsset ->
                    val symbolToShow = chains.flatMap { it.assets }
                        .firstOrNull { it.id == (initialSelectedAssetId) }
                        ?.symbolToShow
                    val chainsWithAsset = chains.filter {
                        when (val chainEcosystem = it.ecosystem()) {
                            ChainEcosystem.POLKADOT,
                            ChainEcosystem.KUSAMA -> {
                                chainEcosystem == chainOfTheAsset.ecosystem() && it.assets.any { it.symbolToShow == symbolToShow }
                            }
                            ChainEcosystem.STANDALONE -> {
                                it.id == chainOfTheAsset.id
                            }
                        }
                    }
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
        val ethBasedChains = chains?.filter { it.isEthereumBased }.orEmpty()
        val filtered = if (meta.ethereumPublicKey == null && ethBasedChains.size != ethBasedChainAccounts.size) {
            val ethChainsWithNoAccounts = ethBasedChains.filter { it.id !in ethBasedChainAccounts.keys }
            chains?.filter { it !in ethChainsWithNoAccounts }
        } else {
            chains
        }
        filtered?.map { it.toChainItemState() }
    }.stateIn(this, SharingStarted.Eagerly, null)

    private val symbolFlow = allChainsFlow.map { chains ->
        (initialSelectedAssetId ?: sharedSendState.assetId)?.let { chainAssetId ->
            chains.firstOrNull { it.assets.any { it.id == chainAssetId } }?.let { chainOfTheAsset ->
                val symbol = chainOfTheAsset.assets.firstOrNull { it.id == (chainAssetId) }?.symbolToShow
                symbol
            }
        }
    }.stateIn(this, SharingStarted.Eagerly, null)

    private val enteredChainQueryFlow = MutableStateFlow("")

    val state = combine(chainsFlow, selectedChainId, enteredChainQueryFlow) { chainItems, selectedChainId, searchQuery ->
        val chains = chainItems
            ?.filter {
                val condition = it.tokenSymbols.values.any { it.contains(searchQuery, true) }

                searchQuery.isEmpty() || it.title.contains(searchQuery, true) || condition
            }
            ?.sortedWith(compareBy<ChainItemState> { it.id.defaultChainSort() }.thenBy { it.title })

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

        if (chainId == null) return

        if (!isSelectAsset) {
            assetSpecified(assetId = null, chainId = chainId)
            return
        }

        launch {
            val chain = walletInteractor.getChain(chainId)
            val isChainContainsSelectedAssetId = initialSelectedAssetId in chain.assets.map { it.id }
            if (initialSelectedAssetId == null || !isChainContainsSelectedAssetId) {
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
                assetSpecified(assetId = assetId ?: chain.utilityAsset.id, chainId = chainId)
            }
        }
    }

    private fun assetSpecified(assetId: String?, chainId: ChainId) {
        choiceDone = true
        if (assetId != null) {
            sharedSendState.update(assetId = assetId, chainId = chainId)
        }
        walletRouter.backWithResult(
            WalletRouterApi.KEY_CHAIN_ID to chainId,
            WalletRouterApi.KEY_ASSET_ID to assetId
        )
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
