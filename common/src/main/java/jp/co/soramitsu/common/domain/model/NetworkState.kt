package jp.co.soramitsu.common.domain.model

data class NetworkIssue (
    val type: NetworkIssueType,
    val chainId: String
)

enum class NetworkIssueType {
    Node, Network, Account
}