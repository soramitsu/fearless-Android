package co.jp.soramitsu.walletconnect.domain

import co.jp.soramitsu.walletconnect.model.AppEntity

interface TonConnectRouter {
    fun back()

    fun openTonConnectionDetails(app: AppEntity)
}