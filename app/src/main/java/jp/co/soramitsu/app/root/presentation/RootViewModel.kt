package jp.co.soramitsu.app.root.presentation

import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.rpc.ConnectionManager
import jp.co.soramitsu.common.utils.plusAssign

class RootViewModel(
    private val interactor: RootInteractor,
    private val connectionManager: ConnectionManager
) : BaseViewModel() {
    init {
        bindSocketToLink()
    }

    private fun bindSocketToLink() {
        disposables += interactor.observeSelectedNode()
            .subscribe {
                if (connectionManager.started()) {
                    connectionManager.switchUrl(it.link)
                } else {
                    connectionManager.start(it.link)
                }
            }
    }

    fun jsonFileOpened(content: String?) {}

    override fun onCleared() {
        super.onCleared()

        connectionManager.stop()
    }
}