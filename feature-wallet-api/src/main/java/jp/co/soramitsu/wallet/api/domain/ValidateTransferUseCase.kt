package jp.co.soramitsu.wallet.api.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.DeadRecipientEthereumException
import jp.co.soramitsu.common.validation.DeadRecipientException
import jp.co.soramitsu.common.validation.ExistentialDepositCrossedException
import jp.co.soramitsu.common.validation.ExistentialDepositCrossedWarning
import jp.co.soramitsu.common.validation.SpendInsufficientBalanceException
import jp.co.soramitsu.common.validation.SubstrateBridgeAmountLessThenFeeException
import jp.co.soramitsu.common.validation.SubstrateBridgeMinimumAmountRequired
import jp.co.soramitsu.common.validation.TransferAddressNotValidException
import jp.co.soramitsu.common.validation.TransferToTheSameAddressException
import jp.co.soramitsu.common.validation.WaitForFeeCalculationException
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.wallet.impl.domain.model.Asset

interface ValidateTransferUseCase {
    suspend operator fun invoke(
        amountInPlanks: BigInteger,
        originAsset: Asset,
        destinationChainId: ChainId,
        destinationAddress: String,
        originAddress: String,
        originFee: BigInteger?,
        confirmedValidations: List<TransferValidationResult> = emptyList(),
        transferMyselfAvailable: Boolean,
        skipEdValidation: Boolean = false,
        destinationFee: BigDecimal? = null,
    ): Result<TransferValidationResult>

    suspend fun validateExistentialDeposit(
        amountInPlanks: BigInteger,
        originAsset: Asset,
        destinationChainId: ChainId,
        destinationAddress: String,
        originAddress: String,
        originFee: BigInteger,
        confirmedValidations: List<TransferValidationResult> = emptyList()
    ): Result<TransferValidationResult>
}

sealed class TransferValidationResult {
    object Valid : TransferValidationResult()
    object InsufficientBalance : TransferValidationResult()
    object InsufficientUtilityAssetBalance : TransferValidationResult()
    data class SubstrateBridgeMinimumAmountRequired(val amount: String): TransferValidationResult()
    data class SubstrateBridgeAmountLessThenFeeWarning(val chainName: String): TransferValidationResult()
    data class ExistentialDepositWarning(val edAmount: String) : TransferValidationResult()
    data class ExistentialDepositError(val edAmount: String) : TransferValidationResult()
    data class UtilityExistentialDepositWarning(val edAmount: String) : TransferValidationResult()
    data class UtilityExistentialDepositError(val edAmount: String) : TransferValidationResult()
    data class DeadRecipient(val resultAmount: String, val edAmount: String, val extraAmount: String) : TransferValidationResult()
    object DeadRecipientEthereum : TransferValidationResult()
    object InvalidAddress : TransferValidationResult()
    object TransferToTheSameAddress : TransferValidationResult()
    object WaitForFee : TransferValidationResult()

    val isExistentialDepositWarning: Boolean
        get() = this is ExistentialDepositWarning || this is UtilityExistentialDepositWarning
}

fun ValidationException.Companion.fromValidationResult(result: TransferValidationResult, resourceManager: ResourceManager): ValidationException? {
    return when (result) {
        TransferValidationResult.Valid -> null
        TransferValidationResult.InsufficientBalance -> SpendInsufficientBalanceException(resourceManager)
        TransferValidationResult.InsufficientUtilityAssetBalance -> SpendInsufficientBalanceException(resourceManager)
        is TransferValidationResult.SubstrateBridgeMinimumAmountRequired -> SubstrateBridgeMinimumAmountRequired(resourceManager, result.amount)
        is TransferValidationResult.SubstrateBridgeAmountLessThenFeeWarning -> SubstrateBridgeAmountLessThenFeeException(resourceManager, result.chainName)
        is TransferValidationResult.ExistentialDepositWarning -> ExistentialDepositCrossedWarning(resourceManager, result.edAmount)
        is TransferValidationResult.UtilityExistentialDepositWarning -> ExistentialDepositCrossedWarning(resourceManager, result.edAmount)
        is TransferValidationResult.ExistentialDepositError -> ExistentialDepositCrossedException(resourceManager, result.edAmount)
        is TransferValidationResult.UtilityExistentialDepositError -> ExistentialDepositCrossedException(resourceManager, result.edAmount)
        is TransferValidationResult.DeadRecipient -> DeadRecipientException(resourceManager, result.resultAmount, result.edAmount, result.extraAmount)
        TransferValidationResult.InvalidAddress -> TransferAddressNotValidException(resourceManager)
        TransferValidationResult.WaitForFee -> WaitForFeeCalculationException(resourceManager)
        TransferValidationResult.TransferToTheSameAddress -> TransferToTheSameAddressException(resourceManager)
        TransferValidationResult.DeadRecipientEthereum -> DeadRecipientEthereumException(resourceManager)
    }
}
