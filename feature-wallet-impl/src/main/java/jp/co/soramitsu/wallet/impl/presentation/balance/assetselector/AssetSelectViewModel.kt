package jp.co.soramitsu.wallet.impl.presentation.balance.assetselector

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.XcmInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import jp.co.soramitsu.wallet.api.presentation.WalletRouter as WalletRouterApi

@HiltViewModel
class AssetSelectViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    walletInteractor: WalletInteractor,
    polkaswapInteractor: PolkaswapInteractor,
    xcmInteractor: XcmInteractor,
    savedStateHandle: SavedStateHandle,
    private val sharedSendState: SendSharedState
) : BaseViewModel(), AssetSelectContentInterface {

    private var choiceDone = false

    private val filterChainId: String? = savedStateHandle[AssetSelectFragment.KEY_FILTER_CHAIN_ID]
    private val isFilterXcmAssets: Boolean = savedStateHandle[AssetSelectFragment.KEY_IS_FILTER_XCM_ASSETS] ?: false
    private val swapQuickChainsIds: List<ChainId> = savedStateHandle[AssetSelectFragment.KEY_SWAP_QUICK_CHAIN_IDS] ?: emptyList()

    private val initialSelectedAssetId: String? = savedStateHandle[AssetSelectFragment.KEY_SELECTED_ASSET_ID]
    private val excludeAssetId: String? = savedStateHandle[AssetSelectFragment.KEY_EXCLUDE_ASSET_ID]
    private val selectedAssetIdFlow = MutableStateFlow(initialSelectedAssetId)
    private val selectedChainIdFlow = MutableStateFlow(filterChainId)

    private val okxChainItems = walletInteractor.observeOkxChains().mapList {
        ChainItemState(it.id, it.icon, it.rank)
    }

    private val polkaswapChainItemFlow = flowOf {
        walletInteractor.getChain(polkaswapInteractor.polkaswapChainId)
    }.map {
        ChainItemState(it.id, it.icon, it.rank)
    }

    private val enteredTokenQueryFlow = MutableStateFlow("")

    private val chainQuickSelectIdsFlow = if (swapQuickChainsIds.isEmpty()) {
        flowOf { emptyList() }
    } else {
        combine(
            polkaswapChainItemFlow,
            okxChainItems,
            selectedChainIdFlow
        ) { polkaswapChain, okxChains, selectedChainId ->
            val chainsToShow = okxChains
                .plus(polkaswapChain)
                .filter { it.id in swapQuickChainsIds }
                .filter {
                    it.rank != null || it.id == selectedChainId
                }.sortedBy {
                    it.rank ?: 0
                }.map {
                    if (it.id == selectedChainId) {
                        it.copy(selected = true)
                    } else {
                        it
                    }
                }

            if (selectedChainIdFlow.value == null) {
                selectedChainIdFlow.value = chainsToShow.firstOrNull()?.id
            }
            chainsToShow
        }
    }

    private val isSwap = swapQuickChainsIds.isNotEmpty()

    private val okxAssetsFlow = flowOf {
        walletInteractor.getOkxTokens()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val assetModelsFlow: Flow<List<AssetModel>> =
        if (isFilterXcmAssets) {
            selectedChainIdFlow.flatMapLatest { selectedChainId ->
                xcmInteractor.getAvailableAssetsFlow(originChainId = selectedChainId)
            }
        }
        else {
            println("!!! ELSE assetModelsFlow")
            walletInteractor.assetsFlow()
        }
            .mapList {
                when {
                    it.hasAccount -> it.asset
                    else -> null
                }
            }
            .map { it.filterNotNull().filter { asset -> asset.enabled == true } }
            .mapList { mapAssetToAssetModel(it) }

    val assetItemsState: Flow<List<AssetItemState>> = combine(
        assetModelsFlow,
        okxAssetsFlow,
        selectedChainIdFlow,
        selectedAssetIdFlow,
        enteredTokenQueryFlow
    ) { assets, okxAssets, selectedChainId, selectedAssetId, searchQuery ->
        if (isSwap && selectedChainId != polkaswapInteractor.polkaswapChainId) {
            val chain = selectedChainId?.let { walletInteractor.getChain(it) }
            okxAssets.filter {
                it.chainId == selectedChainId
            }.map {
                AssetItemState(
                    id = it.address,
                    imageUrl = it.logoUrl,
                    chainName = chain?.name,
                    symbol = it.symbol,
                    amount = "",
                    fiatAmount = "",
                    isSelected = it.address == selectedAssetId,
                    chainId = it.chainId
                )
            }
        } else {
            assets.filter {
                selectedChainId == null || it.token.configuration.chainId == selectedChainId
            }
                .filter {
                    searchQuery.isEmpty() ||
                            it.token.configuration.symbol.contains(searchQuery, true) ||
                            it.token.configuration.name.orEmpty().contains(searchQuery, true)
                }
                .filter { it.token.configuration.id != excludeAssetId }
                .sortedWith(compareByDescending<AssetModel> { it.fiatAmount.orZero() }.thenBy { it.token.configuration.chainName })
                .map {
                    it.toAssetItemState(isChainNameVisible = !isFilterXcmAssets)
                }
                .toList()
        }
    }

    val state = combine(
        chainQuickSelectIdsFlow,
        assetItemsState,
        selectedAssetIdFlow,
        enteredTokenQueryFlow
    ) { swapChains, assetItems, selectedAssetId, searchQuery ->
        AssetSelectScreenViewState(
            chains = swapChains,
            assets = assetItems,
            selectedAssetId = selectedAssetId,
            searchQuery = searchQuery,
            showAllChains = false
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AssetSelectScreenViewState.default)

    private fun AssetModel.toAssetItemState(isChainNameVisible: Boolean) = AssetItemState(
        id = token.configuration.id,
        imageUrl = token.configuration.iconUrl,
        chainName = token.configuration.chainName.takeIf { isChainNameVisible },
        symbol = token.configuration.symbol.uppercase(),
        amount = available.orZero().formatCrypto(),
        fiatAmount = getAsFiatWithCurrency(available) ?: "${token.fiatSymbol.orEmpty()}0",
        isSelected = false,
        chainId = token.configuration.chainId
    )

    override fun onAssetSelected(assetItemState: AssetItemState) {
        choiceDone = true
        if (selectedAssetIdFlow.value != assetItemState.id) {
            selectedAssetIdFlow.value = assetItemState.id

            sharedSendState.update(chainId = assetItemState.chainId, assetId = assetItemState.id)
        }

        walletRouter.backWithResult(
            WalletRouterApi.KEY_CHAIN_ID to assetItemState.chainId,
            WalletRouterApi.KEY_ASSET_ID to assetItemState.id
        )
    }

    override fun onSearchInput(input: String) {
        enteredTokenQueryFlow.value = input
    }

    override fun onChainSelected(chainId: ChainId) {
        selectedChainIdFlow.value = chainId
    }

    override fun onCloseClick() {
        walletRouter.back()
    }

    fun onDialogClose() {
        if (!choiceDone && sharedSendState.assetId == null) {
            walletRouter.popOutOfSend()
        }
    }
}
