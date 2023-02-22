package jp.co.soramitsu.wallet.impl.presentation.transaction.detail.swap

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.toMarkets
import jp.co.soramitsu.wallet.api.presentation.formatters.tokenAmountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import jp.co.soramitsu.common.compose.component.GradientIconState

@HiltViewModel
class SwapDetailViewModel @Inject constructor(
    private val router: WalletRouter,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(), SwapDetailCallbacks {

    private val swap = savedStateHandle.get<OperationParcelizeModel.Swap>(SwapDetailFragment.KEY_SWAP) ?: error("Swap detail not specified")

    private val swapRate = swap.targetAsset?.amountFromPlanks(swap.targetAssetAmount.orZero()).orZero() / swap.chainAsset.amountFromPlanks(swap.baseAssetAmount)

    val state = MutableStateFlow(
        SwapDetailState(
            fromTokenImage = GradientIconState.Remote(swap.chainAsset.iconUrl, swap.chainAsset.color),
            toTokenImage = GradientIconState.Remote(swap.targetAsset?.iconUrl.orEmpty(), swap.targetAsset?.color.orEmpty()),
            fromTokenAmount = swap.baseAssetAmount.tokenAmountFromPlanks(swap.chainAsset).format(),
            toTokenAmount = swap.targetAsset?.let { swap.targetAssetAmount?.tokenAmountFromPlanks(it) }?.format().orEmpty(),
            fromTokenName = swap.chainAsset.symbolToShow.uppercase(),
            toTokenName = swap.targetAsset?.symbolToShow?.uppercase().orEmpty(),
            statusAppearance = swap.status.mapToStatusAppearance(),
            address = swap.address,
            fromTokenOnToToken = swapRate.format(),
            liquidityProviderFee = swap.liquidityProviderFee.tokenAmountFromPlanks(swap.chainAsset),
            networkFee = swap.networkFee.tokenAmountFromPlanks(swap.chainAsset),
            time = swap.time,
            market = swap.selectedMarket?.let { listOf(it).toMarkets().firstOrNull() } ?: Market.SMART
        )
    )

    override fun onBackClick() {
        router.back()
    }

    override fun onCloseClick() {
        onBackClick()
    }
}

enum class SwapStatusAppearance(
    val color: Color,
    @StringRes val labelRes: Int
) {
    COMPLETED(greenText, R.string.polkaswap_confirmation_swapped_stub),
    PENDING(white, R.string.transaction_status_pending),
    FAILED(white, R.string.transaction_status_failed)
}

private fun Operation.Status.mapToStatusAppearance() = when (this) {
    Operation.Status.COMPLETED -> SwapStatusAppearance.COMPLETED
    Operation.Status.PENDING -> SwapStatusAppearance.PENDING
    Operation.Status.FAILED -> SwapStatusAppearance.FAILED
}
