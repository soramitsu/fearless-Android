package jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog.model

import jp.co.soramitsu.feature_account_api.domain.model.Node

data class NetworkTypeChooserDialogData(
    val selectedNode: Node,
    val nodes: List<Node>
)