package jp.co.soramitsu.feature_staking_impl.presentation.confirm.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.confirm.ConfirmStakingFragment

@Subcomponent(
    modules = [
        ConfirmStakingModule::class
    ]
)
@ScreenScope
interface ConfirmStakingComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): ConfirmStakingComponent
    }

    fun inject(fragment: ConfirmStakingFragment)
}
