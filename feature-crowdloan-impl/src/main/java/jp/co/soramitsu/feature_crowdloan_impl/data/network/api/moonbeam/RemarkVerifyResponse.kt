package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam

class RemarkVerifyResponse(
    val address: String,
    val blockHash: String,
    val extrinsicHash: String,
    val verified: Boolean
)
