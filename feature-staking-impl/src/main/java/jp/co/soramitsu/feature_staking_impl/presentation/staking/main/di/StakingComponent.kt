package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.StakingFragment

@Subcomponent(
    modules = [
        StakingModule::class
    ]
)
@ScreenScope
interface StakingComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): StakingComponent
    }

    fun inject(fragment: StakingFragment)
}
