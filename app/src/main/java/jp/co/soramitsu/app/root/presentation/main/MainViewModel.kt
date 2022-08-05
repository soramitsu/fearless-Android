package jp.co.soramitsu.app.root.presentation.main

import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val interactor: RootInteractor,
    externalRequirements: MutableStateFlow<ChainConnection.ExternalRequirement>,
) : BaseViewModel() {

    init {
        externalRequirements.value = ChainConnection.ExternalRequirement.ALLOWED
    }

    val stakingAvailableLiveData = interactor.stakingAvailableFlow()
        .asLiveData()
}
