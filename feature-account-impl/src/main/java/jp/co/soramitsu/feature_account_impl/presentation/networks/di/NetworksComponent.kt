package jp.co.soramitsu.feature_account_impl.presentation.networks.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.networks.NetworksFragment

@Subcomponent(
    modules = [
        NetworksModule::class
    ]
)
@ScreenScope
interface NetworksComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): NetworksComponent
    }

    fun inject(fragment: NetworksFragment)
}