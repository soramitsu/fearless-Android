package jp.co.soramitsu.app.root.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import java.util.Date
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.domain.GetAppVersion
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection.ExternalRequirement
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class RootViewModel(
    private val interactor: RootInteractor,
    private val rootRouter: RootRouter,
    private val externalConnectionRequirementFlow: MutableStateFlow<ExternalRequirement>,
    private val resourceManager: ResourceManager,
    private val networkStateMixin: NetworkStateMixin,
    private val getAppVersion: GetAppVersion,
) : BaseViewModel(), NetworkStateUi by networkStateMixin {
    companion object {
        private const val IDLE_MINUTES: Long = 20
    }

    private var willBeClearedForLanguageChange = false
    private var timeInBackground: Date? = null

    private val _showUnsupportedAppVersionAlert = MutableLiveData<Event<Unit>>()
    val showUnsupportedAppVersionAlert: LiveData<Event<Unit>> = _showUnsupportedAppVersionAlert

    private val _openPlayMarket = MutableLiveData<Event<Unit>>()
    val openPlayMarket: LiveData<Event<Unit>> = _openPlayMarket

    private val _closeApp = MutableLiveData<Event<Unit>>()
    val closeApp: LiveData<Event<Unit>> = _closeApp

    init {
        checkAppVersion()
        interactor.runBalancesUpdate()
            .onEach { handleUpdatesSideEffect(it) }
            .launchIn(this)

        updatePhishingAddresses()
    }

    private fun checkAppVersion() = viewModelScope.launch {
        val appVersion = getAppVersion()
        val appConfig = interactor.getRemoteConfig()
        val isCurrentVersionSupported = appConfig.excludedVersions.contains(appVersion).not()

        if (isCurrentVersionSupported.not()) {
            _showUnsupportedAppVersionAlert.value = Event(Unit)
        }
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

    fun jsonFileOpened(content: String?) {}

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
}
