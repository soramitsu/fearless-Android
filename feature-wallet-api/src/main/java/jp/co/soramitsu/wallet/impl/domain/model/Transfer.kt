package jp.co.soramitsu.wallet.impl.domain.model

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.Asset

class Transfer(
    val sender: String,
    val recipient: String,
    val amount: BigDecimal,
    val chainAsset: Asset,
    val comment: String? = null,
    val estimateFee: BigDecimal? = null,
    val maxAmountIn: BigDecimal? = null
) {

    val amountInPlanks: BigInteger = chainAsset.planksFromAmount(amount)
    val estimateFeeInPlanks: BigInteger? = estimateFee?.let { chainAsset.planksFromAmount(it) }
    val maxAmountInInPlanks: BigInteger? = maxAmountIn?.let { chainAsset.planksFromAmount(it) }

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
