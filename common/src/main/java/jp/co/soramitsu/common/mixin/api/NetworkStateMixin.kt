package jp.co.soramitsu.common.mixin.api

import jp.co.soramitsu.common.compose.component.NetworkIssueItemState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NetworkStateMixin : NetworkStateUi

interface NetworkStateUi {
    val showConnectingBarFlow: StateFlow<Boolean>

    val networkIssuesFlow: Flow<Set<NetworkIssueItemState>>

    val chainConnectionsFlow: StateFlow<Map<String, Boolean>>

    fun updateShowConnecting(isShow: Boolean)

    fun updateNetworkIssues(list: List<NetworkIssueItemState>)

    fun updateChainConnection(map: Map<String, Boolean>)

    fun notifyAssetsProblem(items: Set<NetworkIssueItemState>)

    fun isAssetHasProblems(assetId: String): Boolean
}
