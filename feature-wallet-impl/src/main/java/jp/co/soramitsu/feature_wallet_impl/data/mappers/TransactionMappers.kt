package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
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