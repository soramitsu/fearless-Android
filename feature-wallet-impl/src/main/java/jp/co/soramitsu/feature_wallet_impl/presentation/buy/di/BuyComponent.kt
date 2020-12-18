package jp.co.soramitsu.feature_wallet_impl.presentation.buy.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.buy.BuyFragment

@Subcomponent(
    modules = [
        BuyModule::class
    ]
)
@ScreenScope
interface BuyComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): BuyComponent
    }

    fun inject(fragment: BuyFragment)
}