package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.Transfer
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel

fun Transaction.toUI(): TransactionModel {
    return TransactionModel(
        hash = hash,
        token = token,
        senderAddress = senderAddress,
        recipientAddress = recipientAddress,
        isIncome = isIncome,
        date = date,
        amount = amount
    )
}

fun TransactionLocal.toTransaction() : Transaction {
    return Transaction(
        hash = hash,
        isIncome = isIncome,
        recipientAddress = recipientAddress,
        senderAddress = senderAddress,
        amount = amount,
        date = date,
        token = token
    )
}

fun Transaction.toLocal(accountAddress: String) : TransactionLocal {
    return TransactionLocal(
        accountAddress = accountAddress,
        hash = hash,
        isIncome = isIncome,
        recipientAddress = recipientAddress,
        senderAddress = senderAddress,
        amount = amount,
        date = date,
        token = token
    )
}

fun Transfer.toTransaction(account: Account) : Transaction {
    return Transaction(
        hash = hash,
        token = Asset.Token.fromNetworkType(account.network.type),
        date = timeInMillis,
        amount = amount,
        senderAddress = from,
        recipientAddress = to,
        isIncome = account.address == to
    )
}