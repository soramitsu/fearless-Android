package jp.co.soramitsu.app.root.presentation

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection.ExternalRequirement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class RootViewModel(
    private val interactor: RootInteractor,
    private val rootRouter: RootRouter,
    private val externalConnectionRequirementFlow: MutableStateFlow<ExternalRequirement>,
    private val resourceManager: ResourceManager,
    private val networkStateMixin: NetworkStateMixin
) : BaseViewModel(), NetworkStateUi by networkStateMixin {

    private var willBeClearedForLanguageChange = false

    init {
        interactor.runUpdateSystem()
            .onEach { handleUpdatesSideEffect(it) }
            .launchIn(this)

        updatePhishingAddresses()
    }

    private fun handleUpdatesSideEffect(sideEffect: Updater.SideEffect) {
        // pass
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
    }

    fun noticeInForeground() {
        if (externalConnectionRequirementFlow.value == ExternalRequirement.STOPPED) {
            externalConnectionRequirementFlow.value = ExternalRequirement.ALLOWED
        }
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
}
