package jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.confirm.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.confirm.ConfirmRebondFragment
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.confirm.ConfirmRebondPayload

@Subcomponent(
    modules = [
        ConfirmRebondModule::class
    ]
)
@ScreenScope
interface ConfirmRebondComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance payload: ConfirmRebondPayload,
        ): ConfirmRebondComponent
    }

    fun inject(fragment: ConfirmRebondFragment)
}
