package jp.co.soramitsu.nft.impl.domain.usecase.validation

import jp.co.soramitsu.core.utils.isValidAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import java.math.BigInteger
import javax.inject.Inject

class ValidateNFTTransferUseCase @Inject constructor() {

    @Suppress("LongParameterList")
    operator fun invoke(
        chain: Chain,
        recipient: String,
        ownAddress: String,
        utilityAsset: Asset,
        fee: BigInteger,
        skipEdValidation: Boolean,
        balance: BigInteger,
        confirmedValidations: List<TransferValidationResult>,
    ): Result<TransferValidationResult> {
        return runCatching {
            val validateAddressResult = kotlin.runCatching { chain.isValidAddress(recipient) }

            val initialChecks = mapOf(
                TransferValidationResult.InvalidAddress to (validateAddressResult.getOrNull() != true),
                TransferValidationResult.TransferToTheSameAddress to (recipient == ownAddress)
            )

            val initialCheck = performChecks(initialChecks, confirmedValidations, skipEdValidation)
            if (initialCheck != TransferValidationResult.Valid) {
                return Result.success(initialCheck)
            }

            val validationChecks = mapOf(
                TransferValidationResult.InsufficientBalance to (BigInteger.ONE > balance),
                TransferValidationResult.InsufficientUtilityAssetBalance to (fee > utilityAsset.transferableInPlanks)
            )

            val result = performChecks(validationChecks, confirmedValidations, skipEdValidation)
            return Result.success(result)
        }
    }

    private fun performChecks(
        checks: Map<TransferValidationResult, Boolean>,
        confirmedValidations: List<TransferValidationResult>,
        skipEdValidation: Boolean = false
    ): TransferValidationResult {
        checks.filterNot { (result, _) ->
            result in confirmedValidations || skipEdValidation && result.isExistentialDepositWarning
        }.forEach { (result, condition) ->
            if (condition) return result
        }
        return TransferValidationResult.Valid
    }
}
