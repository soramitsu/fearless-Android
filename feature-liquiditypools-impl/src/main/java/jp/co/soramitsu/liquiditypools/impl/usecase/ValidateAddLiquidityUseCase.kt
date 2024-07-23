package jp.co.soramitsu.liquiditypools.impl.usecase

import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus

class ValidateAddLiquidityUseCase @Inject constructor() {

    operator fun invoke(
        assetFrom: AssetWithStatus,
        assetTo: AssetWithStatus,
        utilityAssetId: String,
        utilityAmount: BigDecimal,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        feeAmount: BigDecimal
    ): Result<TransferValidationResult> {
        return runCatching {
            val isEnoughAmountFrom = amountFrom + feeAmount.takeIf {
                assetFrom.asset.token.configuration.id == utilityAssetId
            }.orZero() < assetFrom.asset.total.orZero()

            val isEnoughAmountTo = amountTo + feeAmount.takeIf {
                assetTo.asset.token.configuration.id == utilityAssetId
            }.orZero() < assetTo.asset.total.orZero()

            val isEnoughAmountFee = if (utilityAssetId in listOf(assetFrom.asset.token.configuration.id, assetTo.asset.token.configuration.id)) {
                true
            } else {
                feeAmount < utilityAmount
            }

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