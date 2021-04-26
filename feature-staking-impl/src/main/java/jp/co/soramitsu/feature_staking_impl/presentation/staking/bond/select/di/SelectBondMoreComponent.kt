package jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select.SelectBondMoreFragment

@Subcomponent(
    modules = [
        SelectBondMoreModule::class
    ]
)
@ScreenScope
interface SelectBondMoreComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): SelectBondMoreComponent
    }

    fun inject(fragment: SelectBondMoreFragment)
}
