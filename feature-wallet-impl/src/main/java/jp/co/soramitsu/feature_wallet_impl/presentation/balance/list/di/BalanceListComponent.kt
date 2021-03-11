package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.BalanceListFragment

@Subcomponent(
    modules = [
        BalanceListModule::class
    ]
)
@ScreenScope
interface BalanceListComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): BalanceListComponent
    }

    fun inject(fragment: BalanceListFragment)
}