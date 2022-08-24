package jp.co.soramitsu.crowdloan.impl.data.network.api.moonbeam

class RemarkVerifyRequest(
    val address: String,
    val blockHash: String,
    val extrinsicHash: String
)
