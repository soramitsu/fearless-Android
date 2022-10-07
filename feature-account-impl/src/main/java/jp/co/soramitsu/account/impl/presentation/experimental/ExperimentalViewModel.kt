package jp.co.soramitsu.feature_account_impl.presentation.experimental

import androidx.annotation.DrawableRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.ViewState
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.sendEvent
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.domain.BeaconConnectedUseCase
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

class ExperimentalViewModel(
    private val router: AccountRouter,
    private val isBeaconConnected: BeaconConnectedUseCase
) : BaseViewModel() {

    private val _state = MutableLiveData<ViewState>(ViewState.Empty)
    val state: LiveData<ViewState> = _state

    private val _scanBeaconQrEvent = MutableLiveData<Event<Unit>>()
    val scanBeaconQrEvent: LiveData<Event<Unit>> = _scanBeaconQrEvent

    init {
        val status = if (isBeaconConnected()) ExperimentalState.ExperimentStatus.Connected else ExperimentalState.ExperimentStatus.Disconnected
        _state.value = ExperimentalState(ExperimentalState.ExperimentItem("Beacon Dapp", R.drawable.ic_beacon, status))
    }

    fun onBeaconClicked() {
        if (isBeaconConnected()) {
            router.openBeacon()
        } else {
            _scanBeaconQrEvent.sendEvent()
        }
    }

    fun backClicked() {
        router.back()
    }

    fun beaconQrScanned(qrContent: String) {
        router.openBeacon(qrContent)
    }
}

data class ExperimentalState(val beaconDapp: ExperimentItem?) : ViewState {

    data class ExperimentItem(
        val name: String,
        @DrawableRes val icon: Int,
        val status: ExperimentStatus?
    )

    enum class ExperimentStatus {
        Connected, Disconnected
    }
}
