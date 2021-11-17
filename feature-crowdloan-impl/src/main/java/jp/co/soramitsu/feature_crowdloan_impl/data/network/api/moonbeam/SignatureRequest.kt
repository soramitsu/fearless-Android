package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam

class SignatureRequest(
    val address: String,
    val contribution: String,
    val previousTotalContribution: String,
    val guid: String
)
