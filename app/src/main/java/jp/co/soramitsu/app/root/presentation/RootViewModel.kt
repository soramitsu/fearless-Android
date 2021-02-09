package jp.co.soramitsu.app.root.presentation

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.rpc.ConnectionManager
import jp.co.soramitsu.common.data.network.rpc.LifecycleCondition
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.resources.ResourceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

class RootViewModel(
    private val interactor: RootInteractor,
    private val rootRouter: RootRouter,
    private val connectionManager: ConnectionManager,
    private val resourceManager: ResourceManager,
    private val networkStateMixin: NetworkStateMixin
) : BaseViewModel(), NetworkStateUi by networkStateMixin {

    private var nodeScope = CoroutineScope(EmptyCoroutineContext)

    private var willBeClearedForLanguageChange = false

    init {
        observeAllowedToConnect()
        updatePhishingAddresses()
    }

    private fun observeAllowedToConnect() {
        connectionManager.lifecycleConditionFlow()
            .distinctUntilChanged()
            .onEach { lifecycleCondition ->
                if (lifecycleCondition == LifecycleCondition.ALLOWED) {
                    bindConnectionToNode()
                } else {
                    unbindConnection()
                }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)
    }

    private fun bindConnectionToNode() = nodeScope.launch {
        interactor.selectedNodeFlow()
            .distinctUntilChanged()
            .onEach {
                if (connectionManager.started()) {
                    connectionManager.switchUrl(it.link)
                } else {
                    connectionManager.start(it.link)
                }
            }.flowOn(Dispatchers.IO)
            .collectLatest {
                interactor.listenForUpdates()
            }
    }

    private fun unbindConnection() {
        nodeScope.coroutineContext.cancelChildren()

        connectionManager.stop()
    }

    private fun updatePhishingAddresses() {
        viewModelScope.launch {
            interactor.updatePhishingAddresses()
        }
    }

    fun jsonFileOpened(content: String?) {}

    override fun onCleared() {
        super.onCleared()

        connectionManager.setLifecycleCondition(LifecycleCondition.FORBIDDEN)
    }

    fun noticeInBackground() {
        if (!willBeClearedForLanguageChange) {
            connectionManager.setLifecycleCondition(LifecycleCondition.STOPPED)
        }
    }

    fun noticeInForeground() {
        if (connectionManager.getLifecycleCondition() == LifecycleCondition.STOPPED) {
            connectionManager.setLifecycleCondition(LifecycleCondition.ALLOWED)
        }
    }

    fun noticeLanguageLanguage() {
        willBeClearedForLanguageChange = true
    }

    fun restoredAfterConfigChange() {
        if (willBeClearedForLanguageChange) {
            rootRouter.returnToMain()

            willBeClearedForLanguageChange = false
        }
    }

    fun externalUrlOpened(uri: String) {
        if (interactor.isBuyProviderRedirectLink(uri)) {
            showMessage(resourceManager.getString(R.string.buy_completed))
        }
    }
}