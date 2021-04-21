package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.StakingBalanceFragment

@Subcomponent(
    modules = [
        StakingBalanceModule::class
    ]
)
@ScreenScope
interface StakingBalanceComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): StakingBalanceComponent
    }

    fun inject(fragment: StakingBalanceFragment)
}
