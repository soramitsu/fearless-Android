package jp.co.soramitsu.feature_account_impl.presentation.node.model

data class NodeModel(
    val name: String,
    val link: String,
    val isDefault: Boolean,
    val isActive: Boolean
)
