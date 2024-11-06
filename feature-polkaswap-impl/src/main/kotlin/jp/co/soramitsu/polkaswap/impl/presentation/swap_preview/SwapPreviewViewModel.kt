package jp.co.soramitsu.polkaswap.impl.presentation.swap_preview

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.core.utils.orZero
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.backStrings
import jp.co.soramitsu.polkaswap.api.models.toFilters
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsParcelModel
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens.SwapTokensViewModel
import jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens.SwapTokensViewModel.SwapType
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

    val swapType = when {
        swapDetailsViewState.fromChainId == null -> null
        swapDetailsViewState.fromChainId == polkaswapInteractor.polkaswapChainId -> SwapType.POLKASWAP
        swapDetailsViewState.toChainId != null && swapDetailsViewState.toChainId != swapDetailsViewState.fromChainId -> SwapType.OKX_CROSS_CHAIN
        else -> SwapType.OKX_SWAP
    }

    var isClosing = false

    val state =
        MutableStateFlow(SwapPreviewState(swapDetailsViewState = swapDetailsViewState, networkFee = swapDetailsParcelModel.networkFee, isLoading = false))

    init {
        launch {
            if (swapType == SwapType.OKX_SWAP || swapType == SwapType.OKX_CROSS_CHAIN) {
                swapDetailsViewState.fromChainId?.let {
                    polkaswapInteractor.getOkxAllowance(it, swapDetailsViewState.fromTokenId)
                }
            }
        }
    }

    override fun onApproveClick() {
        launch {
            swapDetailsViewState.fromChainId?.let {
                polkaswapInteractor.approve(it, swapDetailsViewState.fromTokenId, swapDetailsParcelModel.amount)
            }
        }
    }

    override fun onBackClick() {
        onClose(Activity.RESULT_CANCELED)
    }

    fun onDismiss() {
        onClose(Activity.RESULT_CANCELED)
    }

    private fun onClose(resultValue: Int) {
        if (!isClosing) {
            isClosing = true

            polkaswapRouter.backWithResult(SwapPreviewFragment.KEY_SWAP_DETAILS_RESULT to resultValue)
        }
    }

    override fun onConfirmClick() {
        state.value = state.value.copy(isLoading = true)
        when (swapType) {
            SwapType.POLKASWAP -> {
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
                            onClose(Activity.RESULT_OK)
                            polkaswapRouter.openOperationSuccess(it, chainId = polkaswapInteractor.polkaswapChainId)
                        },
                        onFailure = {
                            state.value = state.value.copy(isLoading = false)
                            showError(it)
                        }
                    )
                }
            }
            SwapType.OKX_SWAP -> {

            }
            SwapType.OKX_CROSS_CHAIN -> {

            }
            null -> {}
        }

    }
}
