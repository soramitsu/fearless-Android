package jp.co.soramitsu.featurewalletimpl.data.network.model.request

class TransactionHistoryRequest(
    val address: String,
    val row: Int,
    val page: Int
)
