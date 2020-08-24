package jp.co.soramitsu.core_db.di

import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.dao.UserDao

interface DbApi {

    fun provideDatabase(): AppDatabase

    fun provideUserDao(): UserDao

    fun provideNodeDao(): NodeDao
}