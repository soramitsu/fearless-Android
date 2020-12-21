package jp.co.soramitsu.common.mixin.impl

import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import jp.co.soramitsu.common.data.network.rpc.ConnectionManager
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.utils.asLiveData
import jp.co.soramitsu.fearless_utils.wsrpc.State

private const val ATTEMPT_THRESHOLD = 1

class NetworkStateProvider(
    connectionManager: ConnectionManager
) : NetworkStateMixin {
    override val networkStateDisposable = CompositeDisposable()

    override val showConnectingBarLiveData = connectionManager.observeNetworkState()
        .map { state -> state is State.Attempting && state.attempt > ATTEMPT_THRESHOLD }
        .distinctUntilChanged()
        .observeOn(AndroidSchedulers.mainThread())
        .asLiveData(networkStateDisposable)
}