package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.domain.model.NetworkIssueType
import jp.co.soramitsu.core.models.ChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

class NetworkStateService {

    private val connectionPoolProblems = MutableStateFlow<Set<ChainId>>(emptySet())
    private val chainsSyncProblems = MutableStateFlow<Set<ChainId>>(emptySet())

//    private val _showConnectingBarFlow = MutableStateFlow(false)
//    val showConnectingBarFlow = _showConnectingBarFlow

    val networkIssuesFlow: Flow<Map<ChainId, NetworkIssueType>> = combine(
        connectionPoolProblems,
        chainsSyncProblems
    ) { connectionPoolProblems, chainsSyncProblems ->
        val nodesIssues = connectionPoolProblems.map { it to NetworkIssueType.Node }
        val runtimeIssues = chainsSyncProblems.map { it to NetworkIssueType.Network }
        (nodesIssues + runtimeIssues).toMap()
    }

//    fun updateShowConnecting(isShow: Boolean) {
//        _showConnectingBarFlow.value = isShow
//    }

    fun notifyConnectionProblem(chainId: ChainId) {
        connectionPoolProblems.update { it + chainId }
    }

    fun notifyConnectionSuccess(chainId: ChainId) {
        connectionPoolProblems.update {
            it - chainId
        }
    }

    fun updateNetworkIssues(list: List<ChainId>) {
        connectionPoolProblems.value = list.toSet()
    }

    fun notifyChainSyncProblem(chainId: ChainId) {
        chainsSyncProblems.update {
            it + chainId
        }
    }

    fun notifyChainSyncSuccess(id: ChainId) {
        chainsSyncProblems.update {
            it - id
        }
    }
}
