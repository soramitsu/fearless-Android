package jp.co.soramitsu.runtime.di

import android.content.Context
import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.core_db.dao.RuntimeDao
import jp.co.soramitsu.core_db.dao.StorageDao
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

interface RuntimeDependencies {

    fun networkApiCreator(): NetworkApiCreator

    fun socketService(): SocketService

    fun gson(): Gson

    fun runtimeDao(): RuntimeDao

    fun preferences(): Preferences

    fun fileProvider(): FileProvider

    fun context(): Context

    fun accountRepository(): AccountRepository

    fun substrateCalls(): SubstrateCalls

    fun keypairFactory(): KeypairFactory

    fun storageDao(): StorageDao
}
