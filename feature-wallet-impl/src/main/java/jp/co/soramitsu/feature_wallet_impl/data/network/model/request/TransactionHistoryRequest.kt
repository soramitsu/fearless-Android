package jp.co.soramitsu.feature_wallet_impl.data.network.model.request

class TransactionHistoryRequest(
    val address: String,
    val row: Int,
    val page: Int
)