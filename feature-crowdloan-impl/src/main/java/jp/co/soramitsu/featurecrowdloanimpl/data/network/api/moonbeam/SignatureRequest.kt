package jp.co.soramitsu.featurecrowdloanimpl.data.network.api.moonbeam

class SignatureRequest(
    val address: String,
    val contribution: String,
    val previousTotalContribution: String,
    val guid: String
)
