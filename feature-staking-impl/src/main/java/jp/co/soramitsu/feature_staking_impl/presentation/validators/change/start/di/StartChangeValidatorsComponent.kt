package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.start.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.start.StartChangeValidatorsFragment

@Subcomponent(
    modules = [
        StartChangeValidatorsModule::class
    ]
)
@ScreenScope
interface StartChangeValidatorsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): StartChangeValidatorsComponent
    }

    fun inject(fragment: StartChangeValidatorsFragment)
}
