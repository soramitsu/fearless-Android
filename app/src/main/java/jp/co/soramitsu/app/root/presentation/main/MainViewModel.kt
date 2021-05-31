package jp.co.soramitsu.app.root.presentation.main

import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.rpc.ConnectionManager
import jp.co.soramitsu.common.data.network.rpc.LifecycleCondition

class MainViewModel(
    connectionManager: ConnectionManager,
    interactor: RootInteractor
) : BaseViewModel() {

    init {
        connectionManager.setLifecycleCondition(LifecycleCondition.ALLOWED)
    }

    val stakingAvailableLiveData = interactor.stakingAvailableFlow()
        .asLiveData()

    val crowdloanAvailableLiveData = interactor.crowdloanAvailableFlow()
        .asLiveData()
}
