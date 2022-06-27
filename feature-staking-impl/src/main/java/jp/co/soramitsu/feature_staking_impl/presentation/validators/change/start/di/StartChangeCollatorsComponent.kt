package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.start.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.start.StartChangeCollatorsFragment

@Subcomponent(
    modules = [
        StartChangeCollatorsModule::class
    ]
)
@ScreenScope
interface StartChangeCollatorsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): StartChangeCollatorsComponent
    }

    fun inject(fragment: StartChangeCollatorsFragment)
}
