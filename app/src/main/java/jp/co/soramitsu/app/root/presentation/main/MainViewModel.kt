package jp.co.soramitsu.app.root.presentation.main

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.app.root.presentation.RootRouter
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val interactor: RootInteractor,
    externalRequirements: MutableStateFlow<ChainConnection.ExternalRequirement>,
    private val rootRouter: RootRouter,
) : BaseViewModel() {
    companion object {
        private const val IDLE_MINUTES: Long = 20
    }

    init {
        externalRequirements.value = ChainConnection.ExternalRequirement.ALLOWED
    }

    fun onScreenAppears() {
        startDropSessionTimer()
    }

    private fun startDropSessionTimer() {
        viewModelScope.launch {
            delay(IDLE_MINUTES.toDuration(DurationUnit.MINUTES).inWholeMilliseconds)
            rootRouter.openPincodeCheck()
        }
    }

    val stakingAvailableLiveData = interactor.stakingAvailableFlow()
        .asLiveData()
}
