package jp.co.soramitsu.feature_wallet_impl.presentation.receive.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.ReceiveFragment

@Subcomponent(
    modules = [
        ReceiveModule::class
    ]
)
@ScreenScope
interface ReceiveComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): ReceiveComponent
    }

    fun inject(fragment: ReceiveFragment)
}