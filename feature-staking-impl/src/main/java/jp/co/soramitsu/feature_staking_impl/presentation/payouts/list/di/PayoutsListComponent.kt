package jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.PayoutsListFragment

@Subcomponent(
    modules = [
        PayoutsListModule::class
    ]
)
@ScreenScope
interface PayoutsListComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): PayoutsListComponent
    }

    fun inject(fragment: PayoutsListFragment)
}
