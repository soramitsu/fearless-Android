package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main.BeaconFragment

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
            @BindsInstance qrContent: String?
        ): BeaconComponent
    }

    fun inject(fragment: BeaconFragment)
}
