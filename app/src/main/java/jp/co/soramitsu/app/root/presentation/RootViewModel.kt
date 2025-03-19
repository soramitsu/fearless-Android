package jp.co.soramitsu.app.root.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.walletconnect.web3.wallet.client.Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
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
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonMainnetChainId
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.model.BridgeError
import jp.co.soramitsu.tonconnect.api.model.BridgeMethod
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import kotlin.concurrent.timerTask
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@HiltViewModel
class RootViewModel @Inject constructor(
    private val appInitializer: AppInitializer,
    private val interactor: RootInteractor,
    private val rootRouter: RootRouter,
    private val externalConnectionRequirementFlow: MutableStateFlow<ChainConnection.ExternalRequirement>,
    private val resourceManager: ResourceManager,
    private val tonConnectInteractor: TonConnectInteractor
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
            }
            InitializeResult.Success -> {
                observeWalletConnectEvents()
                observeTonConnectEvents()
            }
        }
    }

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

    private fun observeTonConnectEvents() {
        tonConnectInteractor.eventsFlow().onEach { event ->
            try {
                when(event.method) {
                    BridgeMethod.SEND_TRANSACTION -> {
                        if (event.message.params.size > 1) throw IllegalStateException("Request contains excess transactions. Required: 1, Provided: ${event.message.params.size}")
                        val signRequest = TonConnectSignRequest(event.message.params.first())

                        rootRouter.openTonSignRequestWithResult(DappModel(event.connection), event.method.title, signRequest)
                            .onSuccess { (boc, hex) ->
                                runCatching { tonConnectInteractor.sendBlockchainMessage(tonConnectInteractor.getChain(), boc) }
                                    .onSuccess {
                                        kotlin.runCatching {
                                            tonConnectInteractor.sendDappMessage(event, boc)
                                        }.onFailure {
                                            showError(it)
                                        }.onSuccess {
                                            rootRouter.openOperationSuccess(
                                                hex,
                                                tonMainnetChainId,
                                                resourceManager.getString(R.string.success_message_transaction_sent),
                                                resourceManager.getString(jp.co.soramitsu.feature_tonconnect_impl.R.string.all_done)
                                            )
                                        }
                                    }
                                    .onFailure { showError(it) }
                            }
                            .onFailure {
                                tonConnectInteractor.respondDappError(event, BridgeError.UNKNOWN)
                            }
                    }
                    BridgeMethod.DISCONNECT -> {
                        tonConnectInteractor.disconnect(event.connection.clientId)
                    }
                    BridgeMethod.UNKNOWN -> {}
                }
            } catch (e: Exception){
                showError(e)
            }
        }.launchIn(this)
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
