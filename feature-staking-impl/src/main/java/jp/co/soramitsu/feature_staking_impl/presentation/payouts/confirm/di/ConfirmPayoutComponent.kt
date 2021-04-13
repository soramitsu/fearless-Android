package jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm.ConfirmPayoutFragment
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.model.PendingPayoutParcelable

@Subcomponent(
    modules = [
        ConfirmPayoutModule::class
    ]
)
@ScreenScope
interface ConfirmPayoutComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance payouts: List<PendingPayoutParcelable>
        ): ConfirmPayoutComponent
    }

    fun inject(fragment: ConfirmPayoutFragment)
}
