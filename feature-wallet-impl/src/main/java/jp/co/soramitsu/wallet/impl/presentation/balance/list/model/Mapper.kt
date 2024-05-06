package jp.co.soramitsu.wallet.impl.presentation.balance.list.model

import jp.co.soramitsu.common.domain.model.NetworkIssueType

fun NetworkIssueType.toUiModel() = when (this) {
    NetworkIssueType.Node -> jp.co.soramitsu.common.compose.component.NetworkIssueType.Node
    NetworkIssueType.Network -> jp.co.soramitsu.common.compose.component.NetworkIssueType.Network
    NetworkIssueType.Account -> jp.co.soramitsu.common.compose.component.NetworkIssueType.Account
}