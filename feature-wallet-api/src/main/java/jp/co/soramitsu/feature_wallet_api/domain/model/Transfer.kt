package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal
import java.math.BigInteger

class Transfer(
    val recipient: String,
    val amount: BigDecimal,
    val tokenType: Token.Type
) {

    val amountInPlanks: BigInteger = tokenType.planksFromAmount(amount)

    fun validityStatus(
        senderTransferable: BigDecimal,
        senderTotal: BigDecimal,
        fee: BigDecimal,
        recipientBalance: BigDecimal
    ): TransferValidityStatus {
        val transactionTotal = fee + amount
        val existentialDeposit = tokenType.networkType.runtimeConfiguration.existentialDeposit

        return when {
            transactionTotal > senderTransferable -> TransferValidityLevel.Error.Status.NotEnoughFunds
            recipientBalance + amount < existentialDeposit -> TransferValidityLevel.Error.Status.DeadRecipient
            senderTotal - transactionTotal < existentialDeposit -> TransferValidityLevel.Warning.Status.WillRemoveAccount
            else -> TransferValidityLevel.Ok
        }
    }
}