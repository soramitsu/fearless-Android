package jp.co.soramitsu.app.root.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.data.runtime.RuntimePreparationStatus
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.rpc.ConnectionManager
import jp.co.soramitsu.common.data.network.rpc.LifecycleCondition
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.model.Node
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collect
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

    private val connectionScope = CoroutineScope(Dispatchers.Main)
    private val nodeScope = CoroutineScope(EmptyCoroutineContext)

    private var willBeClearedForLanguageChange = false

    private val _outdatedTypesWarningLiveData = MutableLiveData<Event<Unit>>()
    val outdatedTypesWarningLiveData: LiveData<Event<Unit>> = _outdatedTypesWarningLiveData

    private val _runtimeUpdateFailedLiveData = MutableLiveData<Event<Unit>>()
    val runtimeUpdateFailedLiveData: LiveData<Event<Unit>> = _runtimeUpdateFailedLiveData

    init {
        observeAllowedToConnect()
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
            .launchIn(viewModelScope)
    }

    private fun bindConnectionToNode() = connectionScope.launch {
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
                nodeScope.coroutineContext.cancelChildren()
                interactor.listenForUpdates()
            }
    }

    private fun unbindConnection() {
        nodeScope.coroutineContext.cancelChildren()

                nodeChanged(it.networkType)
    }

    private suspend fun nodeChanged(networkType: Node.NetworkType) {
        val accountListening = nodeScope.async { interactor.listenForAccountUpdates(networkType) }

        val runtimeListening = nodeScope.async {
            interactor.listenForRuntimeUpdates(networkType)
                .collect(::handleRuntimePreparationStatus)
        }

        awaitAll(accountListening, runtimeListening)
    }

    private suspend fun tryPrepareRuntime() {
        val status = interactor.manualRuntimeUpdate()

        handleRuntimePreparationStatus(status)
    }

    @Suppress("NON_EXHAUSTIVE_WHEN")
    private fun handleRuntimePreparationStatus(status: RuntimePreparationStatus) {
        when (status) {
            RuntimePreparationStatus.ERROR -> _runtimeUpdateFailedLiveData.postValue(Event(Unit))
            RuntimePreparationStatus.OUTDATED -> _outdatedTypesWarningLiveData.postValue(Event(Unit))
        }
    }

    private fun unbindConnection() {
        connectionScope.coroutineContext.cancelChildren()

        connectionManager.stop()
    }

    fun jsonFileOpened(content: String?) {}

    override fun onCleared() {
        super.onCleared()

        connectionManager.setLifecycleCondition(LifecycleCondition.FORBIDDEN)
    }

    fun retryConfirmed() {
        nodeScope.launch { tryPrepareRuntime() }
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