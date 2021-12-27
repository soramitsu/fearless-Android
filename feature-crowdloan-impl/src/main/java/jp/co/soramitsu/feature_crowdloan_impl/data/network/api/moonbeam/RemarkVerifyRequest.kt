package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam

class RemarkVerifyRequest(
    val address: String,
    val blockHash: String,
    val extrinsicHash: String
)
