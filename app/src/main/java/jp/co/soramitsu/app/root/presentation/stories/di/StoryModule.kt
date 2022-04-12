package jp.co.soramitsu.app.root.presentation.stories.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.app.root.presentation.stories.NavigatorBackTransition
import jp.co.soramitsu.app.root.presentation.stories.StoryViewModel
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.presentation.StoryGroupModel

@Module(includes = [ViewModelModule::class])
class StoryModule {

    @Provides
    fun provideBackTransition(navigator: Navigator): NavigatorBackTransition = navigator::educationalStoriesCompleted

    @Provides
    @IntoMap
    @ViewModelKey(StoryViewModel::class)
    fun provideViewModel(
        backTransition: NavigatorBackTransition,
        group: StoryGroupModel
    ): ViewModel {
        return StoryViewModel(backTransition, group.stories)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): StoryViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(StoryViewModel::class.java)
    }
}
