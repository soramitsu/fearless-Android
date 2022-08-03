package jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.select.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.select.SelectUnbondFragment
import jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.select.SelectUnbondPayload

@Subcomponent(
    modules = [
        SelectUnbondModule::class
    ]
)
@ScreenScope
interface SelectUnbondComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance payload: SelectUnbondPayload
        ): SelectUnbondComponent
    }

    fun inject(fragment: SelectUnbondFragment)
}
