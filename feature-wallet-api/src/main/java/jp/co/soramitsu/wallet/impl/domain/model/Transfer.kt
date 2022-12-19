package jp.co.soramitsu.wallet.impl.domain.model

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

class Transfer(
    val recipient: String,
    val amount: BigDecimal,
    val chainAsset: Chain.Asset
) {

    val amountInPlanks: BigInteger = chainAsset.planksFromAmount(amount)

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
