package jp.co.soramitsu.common.mixin.impl

import jp.co.soramitsu.common.compose.component.NetworkIssueItemState
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.core.models.ChainId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class NetworkStateProvider : NetworkStateMixin {

    private val connectionPoolProblems = MutableStateFlow<Set<NetworkIssueItemState>>(emptySet())
    private val chainsSyncProblems = MutableStateFlow<Set<NetworkIssueItemState>>(emptySet())
    private val assetsUpdateProblems = MutableStateFlow<Set<NetworkIssueItemState>>(emptySet())

    private val _showConnectingBarFlow = MutableStateFlow(false)
    override val showConnectingBarFlow = _showConnectingBarFlow

    override val networkIssuesFlow = combine(
        connectionPoolProblems,
        assetsUpdateProblems,
        chainsSyncProblems
    ) { connectionPoolProblems, assetsUpdateProblems, chainsSyncProblems ->
        connectionPoolProblems + assetsUpdateProblems + chainsSyncProblems
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

    override fun notifyChainSyncProblem(issue: NetworkIssueItemState) {
        val previousSet = chainsSyncProblems.value
        val newSet = previousSet + issue
        chainsSyncProblems.value = newSet
    }

    override fun notifyChainSyncSuccess(id: ChainId) {
        val newSet = chainsSyncProblems.value.toMutableSet().apply { removeIf { it.chainId == id } }
        chainsSyncProblems.value = newSet
    }
}
