package jp.co.soramitsu.core_db.di

import jp.co.soramitsu.core_db.AppDatabase

interface DbApi {

    fun provideDatabase(): AppDatabase
}