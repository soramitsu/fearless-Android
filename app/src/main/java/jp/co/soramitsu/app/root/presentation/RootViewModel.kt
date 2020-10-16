package jp.co.soramitsu.app.root.presentation

import io.reactivex.disposables.Disposable
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.rpc.ConnectionManager
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.utils.plusAssign

class RootViewModel(
    private val interactor: RootInteractor,
    private val connectionManager: ConnectionManager,
    private val networkStateMixin: NetworkStateMixin
) : BaseViewModel(), NetworkStateUi by networkStateMixin {
    private var socketSourceDisposable: Disposable? = null

    private var willBeClearedForLanguageChange = false

    init {
        observeAllowedToConnect()

        disposables += networkStateMixin.networkStateDisposable
    }

    private fun observeAllowedToConnect() {
        disposables += connectionManager.observeAllowedToConnect()
            .distinctUntilChanged()
            .subscribe { allowed ->
                if (allowed) {
                    bindConnectionToNode()
                } else {
                    unbindConnection()
                }
            }
    }

    private fun bindConnectionToNode() {
        socketSourceDisposable = interactor.observeSelectedNode()
            .subscribe {
                if (connectionManager.started()) {
                    connectionManager.switchUrl(it.link)
                } else {
                    connectionManager.start(it.link)
                }

                listenAccountUpdates()
            }
    }

    private fun listenAccountUpdates() {
        disposables += interactor.listenForAccountUpdates()
            .subscribe()
    }

    private fun unbindConnection() {
        socketSourceDisposable?.dispose()

        connectionManager.stop()
    }

    fun jsonFileOpened(content: String?) {}

    override fun onCleared() {
        super.onCleared()

        if (!willBeClearedForLanguageChange) {
            connectionManager.setAllowedToConnect(false)
        }
    }

    fun noticeLanguageLanguage() {
        willBeClearedForLanguageChange = true
    }
}