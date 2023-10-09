package jp.co.soramitsu.wallet.api.domain

import java.math.BigInteger
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.DeadRecipientEthereumException
import jp.co.soramitsu.common.validation.DeadRecipientException
import jp.co.soramitsu.common.validation.ExistentialDepositCrossedException
import jp.co.soramitsu.common.validation.SpendInsufficientBalanceException
import jp.co.soramitsu.common.validation.TransferAddressNotValidException
import jp.co.soramitsu.common.validation.TransferToTheSameAddressException
import jp.co.soramitsu.common.validation.WaitForFeeCalculationException
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.wallet.impl.domain.model.Asset

interface ValidateTransferUseCase {
    suspend operator fun invoke(
        amountInPlanks: BigInteger,
        asset: Asset,
        destinationChainId: ChainId,
        recipientAddress: String,
        ownAddress: String,
        fee: BigInteger?,
        confirmedValidations: List<TransferValidationResult> = emptyList(),
        transferMyselfAvailable: Boolean
    ): Result<TransferValidationResult>

    suspend fun validateExistentialDeposit(
        amountInPlanks: BigInteger,
        asset: Asset,
        destinationChainId: ChainId,
        recipientAddress: String,
        ownAddress: String,
        fee: BigInteger,
        confirmedValidations: List<TransferValidationResult> = emptyList()
    ): Result<TransferValidationResult>
}

sealed class TransferValidationResult {
    object Valid : TransferValidationResult()
    object InsufficientBalance : TransferValidationResult()
    object InsufficientUtilityAssetBalance : TransferValidationResult()
    data class ExistentialDepositWarning(val edAmount: String) : TransferValidationResult()
    data class UtilityExistentialDepositWarning(val edAmount: String) : TransferValidationResult()
    object DeadRecipient : TransferValidationResult()
    object DeadRecipientEthereum : TransferValidationResult()
    object InvalidAddress : TransferValidationResult()
    object TransferToTheSameAddress : TransferValidationResult()
    object WaitForFee : TransferValidationResult()
}

fun ValidationException.Companion.fromValidationResult(result: TransferValidationResult, resourceManager: ResourceManager): ValidationException? {
    return when (result) {
        TransferValidationResult.Valid -> null
        TransferValidationResult.InsufficientBalance -> SpendInsufficientBalanceException(resourceManager)
        TransferValidationResult.InsufficientUtilityAssetBalance -> SpendInsufficientBalanceException(resourceManager)
        is TransferValidationResult.ExistentialDepositWarning -> ExistentialDepositCrossedException(resourceManager, result.edAmount)
        is TransferValidationResult.UtilityExistentialDepositWarning -> ExistentialDepositCrossedException(resourceManager, result.edAmount)
        TransferValidationResult.DeadRecipient -> DeadRecipientException(resourceManager)
        TransferValidationResult.InvalidAddress -> TransferAddressNotValidException(resourceManager)
        TransferValidationResult.WaitForFee -> WaitForFeeCalculationException(resourceManager)
        TransferValidationResult.TransferToTheSameAddress -> TransferToTheSameAddressException(resourceManager)
        TransferValidationResult.DeadRecipientEthereum -> DeadRecipientEthereumException(resourceManager)
    }
}
