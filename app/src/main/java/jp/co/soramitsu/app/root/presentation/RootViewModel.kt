package jp.co.soramitsu.app.root.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.walletconnect.web3.wallet.client.Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.AppInitializer
import jp.co.soramitsu.app.root.domain.InitializationStep
import jp.co.soramitsu.app.root.domain.InitializeResult
import jp.co.soramitsu.app.root.domain.NotSupportedAppVersionException
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import kotlin.concurrent.timerTask
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class RootViewModel @Inject constructor(
    private val appInitializer: AppInitializer,
    private val interactor: RootInteractor,
    private val rootRouter: RootRouter,
    private val externalConnectionRequirementFlow: MutableStateFlow<ChainConnection.ExternalRequirement>,
    private val resourceManager: ResourceManager
) : BaseViewModel() {
    companion object {
        private const val IDLE_MINUTES: Long = 20
    }

    private var willBeClearedForLanguageChange = false
    private var timeInBackground: Date? = null

    private val _showUnsupportedAppVersionAlert = MutableLiveData<Event<Unit>>()
    val showUnsupportedAppVersionAlert: LiveData<Event<Unit>> = _showUnsupportedAppVersionAlert

    private val _showNoInternetConnectionAlert = MutableLiveData<Event<InitializationStep>>()
    val showNoInternetConnectionAlert: LiveData<Event<InitializationStep>> = _showNoInternetConnectionAlert

    private val _openPlayMarket = MutableLiveData<Event<Unit>>()
    val openPlayMarket: LiveData<Event<Unit>> = _openPlayMarket

    private val _closeApp = MutableLiveData<Event<Unit>>()
    val closeApp: LiveData<Event<Unit>> = _closeApp

    private var timer = Timer()
    private var timerTask: TimerTask? = null

    init {
        viewModelScope.launch {
            startAppInitializer()
        }
        observeWalletConnectEvents()
    }

    private suspend fun startAppInitializer(startFrom: InitializationStep = InitializationStep.All) {
        val result = appInitializer.invoke()
        when(result) {
            is InitializeResult.ErrorCanRetry -> {
                if(result.error is NotSupportedAppVersionException) {
                    _showUnsupportedAppVersionAlert.value = Event(Unit)
                } else {
                    _showNoInternetConnectionAlert.value = Event(result.step)
                }
//                startAppInitializer(result.step)
            }
            InitializeResult.Success -> {

            }
        }
    }

//    private suspend fun isCurrentAppVersionSupporoted(): Boolean {
//        val appConfigResult = interactor.getRemoteConfig()
//        return appConfigResult.getOrNull()?.isCurrentVersionSupported == true
//
//    }

    override fun onCleared() {
        super.onCleared()

        externalConnectionRequirementFlow.value = ChainConnection.ExternalRequirement.FORBIDDEN
    }

    fun noticeInBackground() {
        if (!willBeClearedForLanguageChange) {
            externalConnectionRequirementFlow.value = ChainConnection.ExternalRequirement.STOPPED
        }
        timeInBackground = Date()
    }

    fun noticeInForeground() {
        if (externalConnectionRequirementFlow.value == ChainConnection.ExternalRequirement.STOPPED) {
            externalConnectionRequirementFlow.value = ChainConnection.ExternalRequirement.ALLOWED
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

    fun retryLoadConfigClicked(step: InitializationStep) {
        viewModelScope.launch {
            startAppInitializer(step)
        }
    }

    private val _showConnectingBar = MutableStateFlow<Boolean>(false)
    val showConnectingBar: StateFlow<Boolean> = _showConnectingBar

    fun onNetworkAvailable() {
        _showConnectingBar.update { false }
        // todo this code triggers redundant requests and balance updates. Needs research
//        viewModelScope.launch {
//            checkAppVersion()
//        }
    }

    fun onConnectionLost() {
        _showConnectingBar.update { true }
    }

    private fun observeWalletConnectEvents() {
        WCDelegate.walletEvents.onEach {
            when (it) {
                is Wallet.Model.SessionProposal -> {
                    handleSessionProposal(it)
                }

                is Wallet.Model.SessionRequest -> {
                    handleSessionRequest(it)
                }

                else -> {}
            }
        }.stateIn(this, SharingStarted.Eagerly, null)
    }

    private suspend fun handleSessionRequest(sessionRequest: Wallet.Model.SessionRequest) {
        val pendingListOfSessionRequests =
            interactor.getPendingListOfSessionRequests(sessionRequest.topic)
        if (pendingListOfSessionRequests.isEmpty()) {
            return
        }
        rootRouter.openWalletConnectSessionRequest(sessionRequest.topic)
    }

    private fun handleSessionProposal(sessionProposal: Wallet.Model.SessionProposal) {
        rootRouter.openWalletConnectSessionProposal(sessionProposal.pairingTopic)
    }
}
