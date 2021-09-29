package jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail.BalanceDetailFragment

@Subcomponent(
    modules = [
        BalanceDetailModule::class
    ]
)
@ScreenScope
interface BalanceDetailComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance assetPayload: AssetPayload,
        ): BalanceDetailComponent
    }

    fun inject(fragment: BalanceDetailFragment)
}
