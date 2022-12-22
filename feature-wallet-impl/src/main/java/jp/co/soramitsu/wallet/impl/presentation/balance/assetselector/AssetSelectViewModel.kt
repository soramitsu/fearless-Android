package jp.co.soramitsu.wallet.impl.presentation.balance.assetselector

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import jp.co.soramitsu.wallet.api.presentation.WalletRouter as WalletRouterApi

@HiltViewModel
class AssetSelectViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    private val walletInteractor: WalletInteractor,
    savedStateHandle: SavedStateHandle,
    private val sharedSendState: SendSharedState
) : BaseViewModel(), AssetSelectContentInterface {

    private var choiceDone = false

    private val filterChainId: String? = savedStateHandle[AssetSelectFragment.KEY_FILTER_CHAIN_ID]

    private val initialSelectedAssetId: String? = savedStateHandle[AssetSelectFragment.KEY_SELECTED_ASSET_ID]
    private val selectedAssetIdFlow = MutableStateFlow(initialSelectedAssetId)

    private val assetModelsFlow: Flow<List<AssetModel>> =
        walletInteractor.assetsFlow()
            .mapList {
                when {
                    it.hasAccount -> it.asset
                    else -> null
                }
            }
            .map { it.filterNotNull() }
            .mapList { mapAssetToAssetModel(it) }

    private val enteredTokenQueryFlow = MutableStateFlow("")

    val state = combine(assetModelsFlow, selectedAssetIdFlow, enteredTokenQueryFlow) { assetModels, selectedAssetId, searchQuery ->
        val assets = assetModels
            .filter {
                filterChainId == null || it.token.configuration.chainId == filterChainId
            }
            .filter {
                searchQuery.isEmpty() || it.token.configuration.symbolToShow.contains(searchQuery, true)
            }
            .sortedWith(compareByDescending<AssetModel> { it.fiatAmount.orZero() }.thenBy { it.token.configuration.chainName })
            .map {
                it.toAssetItemState()
            }

        AssetSelectScreenViewState(
            assets = assets,
            selectedAssetId = selectedAssetId,
            searchQuery = searchQuery,
            showAllChains = false
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AssetSelectScreenViewState.default)

    private fun AssetModel.toAssetItemState() = AssetItemState(
        id = token.configuration.id,
        imageUrl = token.configuration.iconUrl,
        chainName = token.configuration.chainName,
        symbol = token.configuration.symbolToShow.uppercase(),
        amount = total.orZero().format(),
        fiatAmount = getAsFiatWithCurrency(total) ?: "${token.fiatSymbol.orEmpty()}0",
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

    fun onDialogClose() {
        if (!choiceDone && sharedSendState.assetId == null) {
            walletRouter.popOutOfSend()
        }
    }
}
