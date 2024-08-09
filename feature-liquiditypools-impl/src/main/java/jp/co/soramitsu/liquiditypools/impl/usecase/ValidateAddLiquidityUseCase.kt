package jp.co.soramitsu.liquiditypools.impl.usecase

import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.domain.model.Asset

class ValidateAddLiquidityUseCase @Inject constructor() {

    operator fun invoke(
        assetBase: Asset,
        assetTarget: Asset,
        utilityAssetId: String,
        utilityAmount: BigDecimal,
        amountBase: BigDecimal,
        amountTarget: BigDecimal,
        feeAmount: BigDecimal
    ): Result<TransferValidationResult> {
        return runCatching {
            val isEnoughAmountBase = amountBase + feeAmount.takeIf {
                assetBase.token.configuration.id == utilityAssetId
            }.orZero() < assetBase.total.orZero()

            val isEnoughAmountTarget = amountTarget + feeAmount.takeIf {
                assetTarget.token.configuration.id == utilityAssetId
            }.orZero() < assetTarget.total.orZero()

            val isEnoughAmountFee = if (utilityAssetId in listOf(assetBase.token.configuration.id, assetTarget.token.configuration.id)) {
                true
            } else {
                feeAmount < utilityAmount
            }

            val validationChecks = mapOf (
                TransferValidationResult.InsufficientBalance to (!isEnoughAmountBase || !isEnoughAmountTarget),
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