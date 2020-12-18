package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.core_db.model.TransactionSource
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.TransactionRemote
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel

fun mapTransactionToTransactionModel(transaction: Transaction): TransactionModel {
    return with(transaction) {
        TransactionModel(
            hash = hash,
            type = type,
            senderAddress = senderAddress,
            recipientAddress = recipientAddress,
            isIncome = isIncome,
            date = date,
            amount = amount,
            status = status,
            fee = fee,
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
            fee = feeInPlanks?.let(token::amountFromPlanks),
            status = status,
            type = token
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
            token = type,
            feeInPlanks = fee?.let(type::planksFromAmount)
        )
    }
}

fun mapTransferToTransaction(transfer: TransactionRemote, account: Account): Transaction {
    val token = Token.Type.fromNetworkType(account.network.type)

    return with(transfer) {
        Transaction(
            hash = hash,
            type = token,
            date = timeInMillis,
            amount = amount,
            status = Transaction.Status.fromSuccess(success),
            senderAddress = from,
            recipientAddress = to,
            fee = token.amountFromPlanks(feeInPlanks),
            isIncome = account.address == to
        )
    }
}