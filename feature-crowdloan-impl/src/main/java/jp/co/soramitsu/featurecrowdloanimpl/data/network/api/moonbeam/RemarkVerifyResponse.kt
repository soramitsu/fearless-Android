package jp.co.soramitsu.featurecrowdloanimpl.data.network.api.moonbeam

class RemarkVerifyResponse(
    val address: String,
    val blockHash: String,
    val extrinsicHash: String,
    val verified: Boolean
)
