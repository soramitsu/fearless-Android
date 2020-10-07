package jp.co.soramitsu.feature_wallet_impl.presentation.send.amount.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.send.amount.ChooseAmountFragment

@Subcomponent(
    modules = [
        ChooseAmountModule::class
    ]
)
@ScreenScope
interface ChooseAmountComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance address: String
        ): ChooseAmountComponent
    }

    fun inject(fragment: ChooseAmountFragment)
}