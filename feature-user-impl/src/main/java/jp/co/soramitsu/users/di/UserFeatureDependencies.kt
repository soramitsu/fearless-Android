package jp.co.soramitsu.users.di

import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.core_db.AppDatabase

interface UserFeatureDependencies {

    fun networkApiCreator(): NetworkApiCreator

    fun db(): AppDatabase
}