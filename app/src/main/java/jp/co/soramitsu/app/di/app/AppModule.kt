package jp.co.soramitsu.app.di.app

import android.content.Context
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.app.App
import jp.co.soramitsu.common.di.scope.ApplicationScope

@Module
class AppModule {

    @ApplicationScope
    @Provides
    fun provideContext(application: App): Context {
        return application
    }
}