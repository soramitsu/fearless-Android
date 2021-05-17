package jp.co.soramitsu.feature_staking_impl.presentation.setup.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.setup.SetupStakingFragment

@Subcomponent(
    modules = [
        SetupStakingModule::class
    ]
)
@ScreenScope
interface SetupStakingComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): SetupStakingComponent
    }

    fun inject(fragment: SetupStakingFragment)
}
