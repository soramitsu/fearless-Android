package jp.co.soramitsu.liquiditypools.impl.usecase

import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import java.math.BigDecimal
import javax.inject.Inject

class ValidateRemoveLiquidityUseCase @Inject constructor() {

    operator fun invoke(
        utilityAmount: BigDecimal,
        userBasePooled: BigDecimal,
        userTargetPooled: BigDecimal,
        amountBase: BigDecimal,
        amountTarget: BigDecimal,
        feeAmount: BigDecimal
    ): Result<TransferValidationResult> {
        return runCatching {
            val isEnoughAmountBase = amountBase <= userBasePooled

            val isEnoughAmountTarget = amountTarget <= userTargetPooled

            val isEnoughAmountFee = feeAmount < utilityAmount

            val validationChecks = mapOf(
                TransferValidationResult.InsufficientBalance to (!isEnoughAmountBase || !isEnoughAmountTarget),
                TransferValidationResult.InsufficientUtilityAssetBalance to !isEnoughAmountFee
            )

            val result = performChecks(validationChecks)
            return Result.success(result)
        }
    }

    private fun performChecks(checks: Map<TransferValidationResult, Boolean>): TransferValidationResult {
        checks.forEach { (result, condition) ->
            if (condition) return result
        }
        return TransferValidationResult.Valid
    }
}
