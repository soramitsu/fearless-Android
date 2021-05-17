package jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.confirm.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.confirm.ConfirmUnbondFragment
import jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.confirm.ConfirmUnbondPayload

@Subcomponent(
    modules = [
        ConfirmUnbondModule::class
    ]
)
@ScreenScope
interface ConfirmUnbondComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance payload: ConfirmUnbondPayload,
        ): ConfirmUnbondComponent
    }

    fun inject(fragment: ConfirmUnbondFragment)
}
