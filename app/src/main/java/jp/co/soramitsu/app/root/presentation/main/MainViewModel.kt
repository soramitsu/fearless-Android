package jp.co.soramitsu.app.root.presentation.main

import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection
import kotlinx.coroutines.flow.MutableStateFlow

class MainViewModel(
    interactor: RootInteractor,
    externalRequirements: MutableStateFlow<ChainConnection.ExternalRequirement>,
) : BaseViewModel() {

    init {
        externalRequirements.value = ChainConnection.ExternalRequirement.ALLOWED
    }

    val stakingAvailableLiveData = interactor.stakingAvailableFlow()
        .asLiveData()
}
