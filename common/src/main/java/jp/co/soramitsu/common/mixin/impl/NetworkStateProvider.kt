package jp.co.soramitsu.common.mixin.impl

import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import jp.co.soramitsu.common.data.network.rpc.ConnectionManager
import jp.co.soramitsu.common.data.network.rpc.State
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.utils.asLiveData

private val STATES_TO_SHOW_BAR = listOf(State.CONNECTING, State.WAITING_RECONNECT)

class NetworkStateProvider(
    connectionManager: ConnectionManager
) : NetworkStateMixin {
    override val networkStateDisposable = CompositeDisposable()

    override val showConnectingBarLiveData = connectionManager.observeNetworkState()
        .map { state -> state in STATES_TO_SHOW_BAR }
        .observeOn(AndroidSchedulers.mainThread())
        .asLiveData(networkStateDisposable)
}