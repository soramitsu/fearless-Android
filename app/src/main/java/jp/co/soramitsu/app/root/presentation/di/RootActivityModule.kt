package jp.co.soramitsu.app.root.presentation.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.app.root.presentation.RootRouter

@InstallIn(SingletonComponent::class)
@Module
class RootActivityModule {

    @Provides
    fun provideRootRouter(navigator: Navigator): RootRouter = navigator
}
