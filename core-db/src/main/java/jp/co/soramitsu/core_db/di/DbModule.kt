package jp.co.soramitsu.core_db.di

import android.content.Context
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.core_db.AppDatabase

@Module
class DbModule {

    @Provides
    @ApplicationScope
    fun provideAppDatabase(context: Context): AppDatabase {
        return AppDatabase.get(context)
    }
}