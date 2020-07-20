package jp.co.soramitsu.app.di.app

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.app.navigation.Navigator
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.splash.SplashRouter

@Module
class NavigationModule {

    @ApplicationScope
    @Provides
    fun provideNavigator(): Navigator = Navigator()

    @ApplicationScope
    @Provides
    fun provideSplashRouter(navigator: Navigator): SplashRouter = navigator
}