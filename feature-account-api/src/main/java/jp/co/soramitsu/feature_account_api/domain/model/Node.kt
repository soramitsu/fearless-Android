package jp.co.soramitsu.feature_account_api.domain.model

data class Node(
    val name: String,
    val networkType: NetworkType,
    val link: String,
    val isDefault: Boolean
)