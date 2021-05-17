package jp.co.soramitsu.feature_staking_impl.presentation.story.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.StakingStoryModel
import jp.co.soramitsu.feature_staking_impl.presentation.story.StoryFragment

@Subcomponent(
    modules = [
        StoryModule::class
    ]
)
@ScreenScope
interface StoryComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance story: StakingStoryModel
        ): StoryComponent
    }

    fun inject(fragment: StoryFragment)
}
