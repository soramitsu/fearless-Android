package jp.co.soramitsu.featurecrowdloanimpl.data.network.api.moonbeam

class SignatureResponse(
    val address: String,
    val contribution: String,
    val previousTotalContribution: String,
    val signature: String,
    val timeStamp: String,
    val guid: String
)
