package jp.co.soramitsu.liquiditypools.impl.usecase

import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus

class ValidateRemoveLiquidityUseCase @Inject constructor() {

    operator fun invoke(
        utilityAmount: BigDecimal,
        userBasePooled: BigDecimal,
        userTargetPooled: BigDecimal,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        feeAmount: BigDecimal
    ): Result<TransferValidationResult> {
        return runCatching {
            val isEnoughAmountFrom = amountFrom <= userBasePooled

            val isEnoughAmountTo = amountTo <= userTargetPooled

            val isEnoughAmountFee = feeAmount < utilityAmount

            val validationChecks = mapOf (
                TransferValidationResult.InsufficientBalance to (!isEnoughAmountFrom || !isEnoughAmountTo),
                TransferValidationResult.InsufficientUtilityAssetBalance to !isEnoughAmountFee
            )

            val result = performChecks(validationChecks)
            return Result.success(result)
        }
    }

    private fun performChecks(
        checks: Map<TransferValidationResult, Boolean>,
    ): TransferValidationResult {
        checks.forEach { (result, condition) ->
            if (condition) return result
        }
        return TransferValidationResult.Valid
    }
}