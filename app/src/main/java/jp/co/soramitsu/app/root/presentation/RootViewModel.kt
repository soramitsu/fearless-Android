package jp.co.soramitsu.app.root.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection.ExternalRequirement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.timerTask
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class RootViewModel(
    private val interactor: RootInteractor,
    private val rootRouter: RootRouter,
    private val externalConnectionRequirementFlow: MutableStateFlow<ExternalRequirement>,
    private val resourceManager: ResourceManager,
    private val networkStateMixin: NetworkStateMixin,
) : BaseViewModel(), NetworkStateUi by networkStateMixin {
    companion object {
        private const val IDLE_MINUTES: Long = 20
    }

    private var willBeClearedForLanguageChange = false
    private var timeInBackground: Date? = null

    private val _showUnsupportedAppVersionAlert = MutableLiveData<Event<Unit>>()
    val showUnsupportedAppVersionAlert: LiveData<Event<Unit>> = _showUnsupportedAppVersionAlert

    private val _showNoInternetConnectionAlert = MutableLiveData<Event<Unit>>()
    val showNoInternetConnectionAlert: LiveData<Event<Unit>> = _showNoInternetConnectionAlert

    private val _openPlayMarket = MutableLiveData<Event<Unit>>()
    val openPlayMarket: LiveData<Event<Unit>> = _openPlayMarket

    private val _closeApp = MutableLiveData<Event<Unit>>()
    val closeApp: LiveData<Event<Unit>> = _closeApp

    private var timer = Timer()
    private var timerTask: TimerTask? = null

    init {
        checkAppVersion()
    }

    private fun checkAppVersion() {
        viewModelScope.launch {
            val appConfigResult = interactor.getRemoteConfig()
            when {
                appConfigResult.isFailure -> {
                    _showNoInternetConnectionAlert.value = Event(Unit)
                }
                appConfigResult.getOrNull()?.isCurrentVersionSupported == false -> {
                    _showUnsupportedAppVersionAlert.value = Event(Unit)
                }
                else -> {
                    runBalancesUpdate()
                }
            }
        }
    }

    private fun runBalancesUpdate() {
        interactor.runBalancesUpdate()
            .onEach { handleUpdatesSideEffect(it) }
            .launchIn(this)

        updatePhishingAddresses()
    }

    private fun handleUpdatesSideEffect(sideEffect: Updater.SideEffect) {
        // pass
        hashCode()
    }

    private fun updatePhishingAddresses() {
        viewModelScope.launch {
            interactor.updatePhishingAddresses()
        }
    }

    override fun onCleared() {
        super.onCleared()

        externalConnectionRequirementFlow.value = ExternalRequirement.FORBIDDEN
    }

    fun noticeInBackground() {
        if (!willBeClearedForLanguageChange) {
            externalConnectionRequirementFlow.value = ExternalRequirement.STOPPED
        }
        timeInBackground = Date()
    }

    fun noticeInForeground() {
        if (externalConnectionRequirementFlow.value == ExternalRequirement.STOPPED) {
            externalConnectionRequirementFlow.value = ExternalRequirement.ALLOWED
        }
        timeInBackground?.let {
            if (idleTimePassedFrom(it)) {
                timerTask?.cancel()
                rootRouter.openPincodeCheck()
            }
        }
        timeInBackground = null
    }

    private fun idleTimePassedFrom(timeInBackground: Date): Boolean {
        return Date().time - timeInBackground.time >= IDLE_MINUTES.toDuration(DurationUnit.MINUTES).inWholeMilliseconds
    }

    fun noticeLanguageLanguage() {
        willBeClearedForLanguageChange = true
    }

    fun restoredAfterConfigChange() {
        if (willBeClearedForLanguageChange) {
            rootRouter.returnToWallet()

            willBeClearedForLanguageChange = false
        }
    }

    fun externalUrlOpened(uri: String) {
        if (interactor.isBuyProviderRedirectLink(uri)) {
            showMessage(resourceManager.getString(R.string.buy_completed))
        }
    }

    fun updateAppClicked() {
        _openPlayMarket.value = Event(Unit)
        _closeApp.value = Event(Unit)
    }

    fun onUserInteractedWithApp() {
        timerTask?.cancel()
        timerTask = createTimerTask()
        timer.schedule(timerTask, IDLE_MINUTES.toDuration(DurationUnit.MINUTES).inWholeMilliseconds)
    }

    private fun createTimerTask() = timerTask {
        viewModelScope.launch(Dispatchers.Main) {
            timeInBackground = null
            rootRouter.openNavGraph()
        }
    }

    fun retryLoadConfigClicked() {
        checkAppVersion()
    }
}
