package jp.co.soramitsu.featurecrowdloanimpl.data.network.api.moonbeam

class RemarkVerifyRequest(
    val address: String,
    val blockHash: String,
    val extrinsicHash: String
)
