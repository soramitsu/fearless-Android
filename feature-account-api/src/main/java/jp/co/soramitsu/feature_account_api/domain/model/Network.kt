package jp.co.soramitsu.feature_account_api.domain.model

data class Network(
    val name: String,
    val networkType: NetworkType,
    val link: String
)