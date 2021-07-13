package jp.co.soramitsu.feature_wallet_api.domain.model

class NewTransfer(
    val amount: String,
    val to: String,
    val from: String,
    val fee: String,
    val block: String,
    val extrinsicId: String,
    val timestamp: String,
    val hash: String
) : HistoryElement
