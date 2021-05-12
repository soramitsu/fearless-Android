package jp.co.soramitsu.feature_staking_impl.presentation.validators.current.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.validators.current.CurrentValidatorsFragment

@Subcomponent(
    modules = [
        CurrentValidatorsModule::class
    ]
)
@ScreenScope
interface CurrentValidatorsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): CurrentValidatorsComponent
    }

    fun inject(fragment: CurrentValidatorsFragment)
}
