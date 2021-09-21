package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.math.BigDecimal
import java.math.BigInteger

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
        existentialDeposit: BigDecimal
    ): TransferValidityStatus {
        val transactionTotal = fee + amount

        return when {
            transactionTotal > senderTransferable -> TransferValidityLevel.Error.Status.NotEnoughFunds
            recipientBalance + amount < existentialDeposit -> TransferValidityLevel.Error.Status.DeadRecipient
            senderTotal - transactionTotal < existentialDeposit -> TransferValidityLevel.Warning.Status.WillRemoveAccount
            else -> TransferValidityLevel.Ok
        }
    }
}
