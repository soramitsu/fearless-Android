package jp.co.soramitsu.feature_staking_impl.presentation.story.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.StakingStoryModel
import jp.co.soramitsu.feature_staking_impl.presentation.story.StoryViewModel

@Module(includes = [ViewModelModule::class])
class StoryModule {

    @Provides
    @IntoMap
    @ViewModelKey(StoryViewModel::class)
    fun provideViewModel(
        router: StakingRouter,
        story: StakingStoryModel
    ): ViewModel {
        return StoryViewModel(router, story)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): StoryViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(StoryViewModel::class.java)
    }
}
