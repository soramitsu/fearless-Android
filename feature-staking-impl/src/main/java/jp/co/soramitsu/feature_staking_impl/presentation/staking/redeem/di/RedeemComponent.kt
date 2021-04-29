package jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem.RedeemFragment

@Subcomponent(
    modules = [
        RedeemModule::class
    ]
)
@ScreenScope
interface RedeemComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): RedeemComponent
    }

    fun inject(fragment: RedeemFragment)
}
