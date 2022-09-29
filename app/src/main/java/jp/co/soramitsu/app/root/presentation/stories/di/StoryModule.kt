package jp.co.soramitsu.app.root.presentation.stories.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.app.root.presentation.stories.NavigatorBackTransition
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule

@InstallIn(SingletonComponent::class)
@Module(includes = [ViewModelModule::class])
class StoryModule {

    @Provides
    fun provideBackTransition(navigator: Navigator): NavigatorBackTransition = navigator::educationalStoriesCompleted
}
