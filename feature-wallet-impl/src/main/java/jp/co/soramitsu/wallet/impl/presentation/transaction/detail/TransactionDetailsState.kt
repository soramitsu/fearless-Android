package jp.co.soramitsu.wallet.impl.presentation.transaction.detail

import jp.co.soramitsu.common.compose.component.AddressDisplayState
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.swap.SwapStatusAppearance

sealed interface TransactionDetailsState {
}

data class TransferDetailsState(
    val hash: TextInputViewState,
    val firstAddress: AddressDisplayState?,
    val secondAddress: AddressDisplayState?,
    val status: OperationStatusAppearance,
    val items: List<TitleValueViewState>,
) : TransactionDetailsState

data class SwapDetailState(
    val fromTokenImage: GradientIconState,
    val toTokenImage: GradientIconState,
    val fromTokenAmount: String,
    val toTokenAmount: String,
    val fromTokenName: String,
    val toTokenName: String,
    val statusAppearance: SwapStatusAppearance,
    val address: String,
    val addressName: String?,
    val hash: String,
    val fromTokenOnToToken: String,
    val liquidityProviderFee: String,
    val networkFee: String,
    val time: Long,
    val market: Market,
    val isShowSubscanButtons: Boolean
): TransactionDetailsState