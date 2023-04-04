package jp.co.soramitsu.common.mixin.impl

import jp.co.soramitsu.common.compose.component.NetworkIssueItemState
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class NetworkStateProvider : NetworkStateMixin {

    private val connectionPoolProblems = MutableStateFlow<Set<NetworkIssueItemState>>(emptySet())
    private val assetsUpdateProblems = MutableStateFlow<Set<NetworkIssueItemState>>(emptySet())

    private val _showConnectingBarFlow = MutableStateFlow(false)
    override val showConnectingBarFlow = _showConnectingBarFlow

    override val networkIssuesFlow = combine(
        connectionPoolProblems,
        assetsUpdateProblems
    ) { connectionPoolProblems, assetsUpdateProblems ->
        connectionPoolProblems + assetsUpdateProblems
    }

    private val _chainConnectionsFlow = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    override val chainConnectionsFlow = _chainConnectionsFlow

    override fun updateShowConnecting(isShow: Boolean) {
        _showConnectingBarFlow.value = isShow
    }

    override fun updateNetworkIssues(list: List<NetworkIssueItemState>) {
        connectionPoolProblems.value = list.toSet()
    }

    override fun updateChainConnection(map: Map<String, Boolean>) {
        _chainConnectionsFlow.value = map
    }

    override fun notifyAssetsProblem(items: Set<NetworkIssueItemState>) {
        assetsUpdateProblems.value = items
    }

    override fun isAssetHasProblems(assetId: String): Boolean {
        val hasConnectionPoolProblems = connectionPoolProblems.value.any { it.assetId == assetId }
        val hasAssetUpdateProblems = assetsUpdateProblems.value.any { it.assetId == assetId }
        return hasConnectionPoolProblems && hasAssetUpdateProblems
    }
}
