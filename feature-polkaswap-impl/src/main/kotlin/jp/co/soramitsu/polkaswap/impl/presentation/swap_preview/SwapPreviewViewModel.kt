package jp.co.soramitsu.polkaswap.impl.presentation.swap_preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.backStrings
import jp.co.soramitsu.polkaswap.api.models.toFilters
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsParcelModel
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SwapPreviewViewModel @Inject constructor(
    private val polkaswapRouter: PolkaswapRouter,
    private val polkaswapInteractor: PolkaswapInteractor,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(), SwapPreviewCallbacks {

    private val swapDetailsViewState = savedStateHandle.get<SwapDetailsViewState>(SwapPreviewFragment.KEY_SWAP_DETAILS)!!
    private val swapDetailsParcelModel = savedStateHandle.get<SwapDetailsParcelModel>(SwapPreviewFragment.KEY_SWAP_DETAILS_PARCEL)!!

    val state = MutableStateFlow(SwapPreviewState(swapDetailsViewState = swapDetailsViewState, isLoading = false))

    override fun onBackClick() {
        polkaswapRouter.back()
    }

    override fun onConfirmClick() {
        state.value = state.value.copy(isLoading = true)
        val markets = if (swapDetailsParcelModel.selectedMarket == Market.SMART) emptyList() else listOf(swapDetailsParcelModel.selectedMarket)
        viewModelScope.launch {
            val swapResult = withContext(Dispatchers.Default) {
                polkaswapInteractor.swap(
                    dexId = swapDetailsParcelModel.dexId,
                    inputAssetId = swapDetailsViewState.fromTokenId,
                    outputAssetId = swapDetailsViewState.toTokenId,
                    amount = swapDetailsParcelModel.amount,
                    limit = swapDetailsParcelModel.minMax.orZero(),
                    filter = markets.toFilters(),
                    markets = markets.backStrings(),
                    desired = swapDetailsParcelModel.desired
                )
            }
            swapResult.fold(
                onSuccess = {
                    polkaswapRouter.returnToAssetDetails()
                    polkaswapRouter.openOperationSuccess(it, chainId = soraMainChainId)
                },
                onFailure = {
                    state.value = state.value.copy(isLoading = false)
                    showError(it)
                }
            )
        }
    }
}
