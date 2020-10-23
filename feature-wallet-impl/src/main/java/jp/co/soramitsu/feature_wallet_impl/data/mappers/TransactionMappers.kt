package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.core_db.model.TransactionSource
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.TransactionRemote
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel

fun mapTransactionToTransactionModel(transaction: Transaction): TransactionModel {
    return with(transaction) {
        TransactionModel(
            hash = hash,
            token = token,
            senderAddress = senderAddress,
            recipientAddress = recipientAddress,
            isIncome = isIncome,
            date = date,
            amount = amount,
            status = status,
            fee = fee.amount,
            total = total
        )
    }
}

fun mapTransactionLocalToTransaction(transactionLocal: TransactionLocal): Transaction {
    return with(transactionLocal) {
        Transaction(
            hash = hash,
            isIncome = recipientAddress == accountAddress,
            recipientAddress = recipientAddress,
            senderAddress = senderAddress,
            amount = amount,
            date = date,
            fee = Fee(feeInPlanks, token),
            status = status,
            token = token
        )
    }
}

fun mapTransactionToTransactionLocal(
    transaction: Transaction,
    accountAddress: String,
    source: TransactionSource
): TransactionLocal {
    return with(transaction) {
        TransactionLocal(
            accountAddress = accountAddress,
            hash = hash,
            recipientAddress = recipientAddress,
            senderAddress = senderAddress,
            status = status,
            amount = amount,
            date = date,
            source = source,
            token = token,
            feeInPlanks = fee.amountInPlanks!!
        )
    }
}

fun mapTransferToTransaction(transfer: TransactionRemote, account: Account): Transaction {
    val token = Asset.Token.fromNetworkType(account.network.type)

    return with(transfer) {
        Transaction(
            hash = hash,
            token = token,
            date = timeInMillis,
            amount = amount,
            status = Transaction.Status.fromSuccess(success),
            senderAddress = from,
            recipientAddress = to,
            fee = Fee(feeInPlanks, token),
            isIncome = account.address == to
        )
    }
}