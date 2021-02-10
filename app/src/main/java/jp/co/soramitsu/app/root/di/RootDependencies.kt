package jp.co.soramitsu.app.root.di

import android.content.Context
import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.rpc.ConnectionManager
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core_db.dao.RuntimeDao
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.di.WalletUpdaters
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry

interface RootDependencies {
    fun networkStateMixin(): NetworkStateMixin

    fun connectionManager(): ConnectionManager

    fun accountRepository(): AccountRepository

    fun walletRepository(): WalletRepository

    fun appLinksProvider(): AppLinksProvider

    fun buyTokenRegistry(): BuyTokenRegistry

    fun resourceManager(): ResourceManager

    fun networkApiCreator(): NetworkApiCreator
    fun socketService(): SocketService
    fun gson(): Gson
    fun runtimeDao(): RuntimeDao
    fun preferences(): Preferences
    fun context(): Context
    fun fileProvider(): FileProvider

    fun walletUpdaters(): WalletUpdaters
}