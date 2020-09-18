package jp.co.soramitsu.feature_account_impl.presentation.nodes.model

import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel

data class NodeModel(
    val name: String,
    val link: String,
    val networkModelType: NetworkModel.NetworkTypeUI
)