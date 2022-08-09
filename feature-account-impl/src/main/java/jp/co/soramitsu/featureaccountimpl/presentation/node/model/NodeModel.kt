package jp.co.soramitsu.featureaccountimpl.presentation.node.model

data class NodeModel(
    val name: String,
    val link: String,
    val isDefault: Boolean,
    val isActive: Boolean
)
