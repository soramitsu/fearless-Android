package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.BeaconFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.ReceiveFragment

@Subcomponent(
    modules = [
        BeaconModule::class
    ]
)
@ScreenScope
interface BeaconComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance qrContent: String
        ): BeaconComponent
    }

    fun inject(fragment: BeaconFragment)
}
