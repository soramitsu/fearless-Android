package jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.confirm.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.confirm.ConfirmRewardDestinationFragment
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.confirm.parcel.ConfirmRewardDestinationPayload

@Subcomponent(
    modules = [
        ConfirmRewardDestinationModule::class
    ]
)
@ScreenScope
interface ConfirmRewardDestinationComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance payload: ConfirmRewardDestinationPayload
        ): ConfirmRewardDestinationComponent
    }

    fun inject(fragment: ConfirmRewardDestinationFragment)
}
