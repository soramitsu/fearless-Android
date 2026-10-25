package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.NotEnoughResultedAmountToPayFeeException
import jp.co.soramitsu.common.validation.SpendInsufficientBalanceException
import jp.co.soramitsu.common.validation.UnableToPayFeeException

internal fun unavailableSwapBalance(resources: ResourceManager) = ValidationException(
    resources.getString(R.string.common_error_general_title),
    resources.getString(R.string.portfolio_balance_unavailable)
)

/** An unknown input or fee holding cannot authorize arithmetic or a swap preview. */
internal fun validateSwapBalances(
    available: BigDecimal?, feeAvailable: BigDecimal?, amount: BigDecimal, output: BigDecimal,
    fee: BigDecimal, inputIsFee: Boolean, outputIsFee: Boolean, resources: ResourceManager
): Throwable? = when {
    available == null || feeAvailable == null -> unavailableSwapBalance(resources)
    amount > available -> SpendInsufficientBalanceException(resources)
    !outputIsFee && feeAvailable <= fee -> UnableToPayFeeException(resources)
    outputIsFee && feeAvailable + output <= fee -> NotEnoughResultedAmountToPayFeeException(resources)
    inputIsFee && available <= amount + fee -> SpendInsufficientBalanceException(resources)
    else -> null
}
