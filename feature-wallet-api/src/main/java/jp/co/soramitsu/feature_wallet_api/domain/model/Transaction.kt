package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal

class Transaction(
    val hash: String,
    val token: Asset.Token,
    val senderAddress: String,
    val recipientAddress: String,
    val amount: BigDecimal,
    val date: Long,
    val isIncome: Boolean
)