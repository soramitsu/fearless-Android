package jp.co.soramitsu.app.root.di

import jp.co.soramitsu.common.data.network.rpc.ConnectionManager
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

interface RootDependencies {
    fun networkStateMixin(): NetworkStateMixin

    fun connectionManager(): ConnectionManager

    fun accountRepository(): AccountRepository
}