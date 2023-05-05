package jp.co.soramitsu.wallet.impl.domain.model

import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import java.math.BigDecimal
import java.math.BigInteger

class CrossChainTransfer(
    val originChainId: ChainId,
    val destinationChainId: ChainId,
    val recipient: String,
    val amount: BigDecimal,
    destinationFee: BigDecimal,
    val chainAsset: Asset
) {

    val fullAmountInPlanks: BigInteger = chainAsset.planksFromAmount(amount) + chainAsset.planksFromAmount(destinationFee)

    fun validityStatus(
        senderTransferable: BigDecimal,
        senderTotal: BigDecimal,
        fee: BigDecimal,
        recipientBalance: BigDecimal,
        existentialDeposit: BigDecimal,
        isUtilityToken: Boolean,
        senderUtilityBalance: BigDecimal,
        utilityExistentialDeposit: BigDecimal,
        tip: BigDecimal? = null
    ): TransferValidityStatus {
        val extraSpends = fee + tip.orZero()

        val transactionTotal = amount + if (isUtilityToken) {
            extraSpends
        } else {
            BigDecimal.ZERO
        }

        return when {
            transactionTotal > senderTransferable -> TransferValidityLevel.Error.Status.NotEnoughFunds
            recipientBalance + amount < existentialDeposit -> TransferValidityLevel.Error.Status.DeadRecipient
            senderTotal - transactionTotal < existentialDeposit -> TransferValidityLevel.Warning.Status.WillRemoveAccount
            !isUtilityToken && (senderUtilityBalance - extraSpends < utilityExistentialDeposit) -> TransferValidityLevel.Warning.Status.WillRemoveAccount
            else -> TransferValidityLevel.Ok
        }
    }
}
