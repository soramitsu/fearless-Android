package jp.co.soramitsu.runtime.di

import android.content.Context
import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.core_db.dao.StorageDao
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService

interface RuntimeDependencies {

    fun networkApiCreator(): NetworkApiCreator

    fun socketServiceCreator(): SocketService

    fun gson(): Gson

    fun preferences(): Preferences

    fun fileProvider(): FileProvider

    fun context(): Context

    fun storageDao(): StorageDao

    fun bulkRetriever(): BulkRetriever

    fun chainDao(): ChainDao
}
