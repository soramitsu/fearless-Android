package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.compose.component.NetworkIssueItemState
import jp.co.soramitsu.core.models.ChainId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class NetworkStateService {

    private val connectionPoolProblems = MutableStateFlow<Set<ChainId>>(emptySet())
    private val chainsSyncProblems = MutableStateFlow<Set<ChainId>>(emptySet())

    private val _showConnectingBarFlow = MutableStateFlow(false)
    val showConnectingBarFlow = _showConnectingBarFlow

    val networkIssuesFlow = combine(
        connectionPoolProblems,
        chainsSyncProblems
    ) { connectionPoolProblems, chainsSyncProblems ->
        connectionPoolProblems  + chainsSyncProblems
    }

    fun updateShowConnecting(isShow: Boolean) {
        _showConnectingBarFlow.value = isShow
    }

    fun updateNetworkIssues(list: List<NetworkIssueItemState>) {
        connectionPoolProblems.value = list.toSet()
    }

    fun notifyChainSyncProblem(issue: NetworkIssueItemState) {
        val previousSet = chainsSyncProblems.value
        val newSet = previousSet + issue
        chainsSyncProblems.value = newSet
    }

    fun notifyChainSyncSuccess(id: ChainId) {
        val newSet = chainsSyncProblems.value.toMutableSet().apply { removeIf { it.chainId == id } }
        chainsSyncProblems.value = newSet
    }
}
