package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeLocalToTokenType
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeToTokenTypeLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.TransactionRemote
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel

fun mapTransactionStatusToTransactionStatusLocal(status: Transaction.Status) = when (status) {
    Transaction.Status.PENDING -> TransactionLocal.Status.PENDING
    Transaction.Status.COMPLETED -> TransactionLocal.Status.COMPLETED
    Transaction.Status.FAILED -> TransactionLocal.Status.FAILED
}

fun mapTransactionStatusLocalToTransactionStatus(status: TransactionLocal.Status) = when (status) {
    TransactionLocal.Status.PENDING -> Transaction.Status.PENDING
    TransactionLocal.Status.COMPLETED -> Transaction.Status.COMPLETED
    TransactionLocal.Status.FAILED -> Transaction.Status.FAILED
}

fun mapTransactionToTransactionModel(transaction: Transaction): TransactionModel {
    return with(transaction) {
        TransactionModel(
            hash = hash,
            type = tokenType,
            senderAddress = senderAddress,
            recipientAddress = recipientAddress,
            accountName = accountName,
            isIncome = isIncome,
            date = date,
            amount = amount,
            status = status,
            fee = fee,
            total = total
        )
    }
}

fun mapTransactionLocalToTransaction(transactionLocal: TransactionLocal, accountName: String?): Transaction {
    val tokenType = mapTokenTypeLocalToTokenType(transactionLocal.token)

    return with(transactionLocal) {
        Transaction(
            hash = hash,
            isIncome = recipientAddress == accountAddress,
            recipientAddress = recipientAddress,
            senderAddress = senderAddress,
            amount = amount,
            date = date,
            fee = feeInPlanks?.let(tokenType::amountFromPlanks),
            status = mapTransactionStatusLocalToTransactionStatus(status),
            tokenType = tokenType,
            accountName = accountName
        )
    }
}

fun mapTransactionToTransactionLocal(
    transaction: Transaction,
    accountAddress: String,
    source: TransactionLocal.Source
): TransactionLocal {
    return with(transaction) {
        TransactionLocal(
            accountAddress = accountAddress,
            hash = hash,
            recipientAddress = recipientAddress,
            senderAddress = senderAddress,
            status = mapTransactionStatusToTransactionStatusLocal(status),
            amount = amount,
            date = date,
            source = source,
            token = mapTokenTypeToTokenTypeLocal(tokenType),
            feeInPlanks = fee?.let(tokenType::planksFromAmount),
            networkType = tokenType.networkType
        )
    }
}

fun mapTransferToTransaction(transfer: TransactionRemote, account: WalletAccount, accountName: String?): Transaction {
    val token = Token.Type.fromNetworkType(account.network.type)

    return with(transfer) {
        Transaction(
            hash = hash,
            tokenType = token,
            date = timeInMillis,
            amount = amount,
            status = Transaction.Status.fromSuccess(success),
            senderAddress = from,
            recipientAddress = to,
            fee = token.amountFromPlanks(feeInPlanks),
            isIncome = account.address == to,
            accountName = accountName
        )
    }
}