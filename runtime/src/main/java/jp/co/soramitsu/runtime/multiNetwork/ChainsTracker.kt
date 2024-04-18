package jp.co.soramitsu.runtime.multiNetwork

import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

object ChainsStateTracker {

    val state = MutableStateFlow<Map<ChainId, ChainState>>(emptyMap())

    fun updateState(chain: Chain, updateBlock: (ChainState) -> ChainState = { it }) {
        state.update { prevMap ->
            val prevState = prevMap.getOrElse(chain.id) { ChainState(chain) }
            val newState = updateBlock(prevState)
            prevMap.toMutableMap().also {
                it[chain.id] = newState
            }
        }
    }

    fun updateState(chainId: ChainId, updateBlock: (ChainState) -> ChainState = { it }) {
        state.update { prevMap ->
            val prevState = prevMap[chainId] ?: return
            val newState = prevState.let(updateBlock)
            prevMap.toMutableMap().also {
                it[chainId] = newState
            }
        }
    }
}

data class ChainState(
    val chain: Chain,
    val connectionStatus: ConnectionStatus? = null,
    val runtimeVersion: Status? = null,
    val downloadMetadata: Status? = null,
    val runtimeConstruction: Status? = null
) {
    sealed interface Status {
        data object Started : Status
        data object Completed : Status
        data class Failed(val error: Throwable) : Status
    }

    sealed interface ConnectionStatus {
        data class Connecting(val node: String) : ConnectionStatus
        data class Paused(val node: String) : ConnectionStatus
        data class Connected(val node: String) : ConnectionStatus

        data object Disconnected : ConnectionStatus
        data object Failed : ConnectionStatus
    }
}