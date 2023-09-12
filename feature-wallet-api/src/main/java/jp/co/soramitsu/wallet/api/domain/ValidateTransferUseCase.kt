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

enum class TransferValidationResult {
    Valid,
    InsufficientBalance,
    InsufficientUtilityAssetBalance,
    ExistentialDepositWarning,
    UtilityExistentialDepositWarning,
    DeadRecipient,
    DeadRecipientEthereum,
    InvalidAddress,
    TransferToTheSameAddress,
    WaitForFee
}

// TODO create errors for utility asset (UtilityExistentialDepositWarning, InsufficientUtilityAssetBalance)
fun ValidationException.Companion.fromValidationResult(result: TransferValidationResult, resourceManager: ResourceManager): ValidationException? {
    return when (result) {
        TransferValidationResult.Valid -> null
        TransferValidationResult.InsufficientBalance -> SpendInsufficientBalanceException(resourceManager)
        TransferValidationResult.InsufficientUtilityAssetBalance -> SpendInsufficientBalanceException(resourceManager)
        TransferValidationResult.ExistentialDepositWarning -> ExistentialDepositCrossedException(resourceManager)
        TransferValidationResult.UtilityExistentialDepositWarning -> ExistentialDepositCrossedException(resourceManager)
        TransferValidationResult.DeadRecipient -> DeadRecipientException(resourceManager)
        TransferValidationResult.InvalidAddress -> TransferAddressNotValidException(resourceManager)
        TransferValidationResult.WaitForFee -> WaitForFeeCalculationException(resourceManager)
        TransferValidationResult.TransferToTheSameAddress -> TransferToTheSameAddressException(resourceManager)
        TransferValidationResult.DeadRecipientEthereum -> DeadRecipientEthereumException(resourceManager)
    }
}
