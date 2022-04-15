package jp.co.soramitsu.app.root.presentation.stories.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.common.presentation.StoryGroupModel

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
            @BindsInstance story: StoryGroupModel
        ): StoryComponent
    }

    fun inject(fragment: jp.co.soramitsu.app.root.presentation.stories.StoryFragment)
}
