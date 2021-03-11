package jp.co.soramitsu.common.mixin.impl

import androidx.lifecycle.asLiveData
import jp.co.soramitsu.common.data.network.rpc.ConnectionManager
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.State
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val ATTEMPT_THRESHOLD = 1

class NetworkStateProvider(
    connectionManager: ConnectionManager
) : NetworkStateMixin {

    override val showConnectingBarLiveData = connectionManager.networkStateFlow()
        .map { state ->
            val attempts = stateAsAttempting(state)

            attempts != null && attempts > ATTEMPT_THRESHOLD
        }
        .distinctUntilChanged()
        .asLiveData()

    private fun stateAsAttempting(state: State): Int? {
        return when (state) {
            is State.Connecting -> state.attempt
            is State.WaitingForReconnect -> state.attempt
            else -> null
        }
    }
}