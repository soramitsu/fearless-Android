package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.sign.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main.BeaconFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.sign.SignBeaconTransactionFragment

@Subcomponent(
    modules = [
        SignBeaconTransactionModule::class
    ]
)
@ScreenScope
interface SignBeaconTransactionComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance payloadToSign: String
        ): SignBeaconTransactionComponent
    }

    fun inject(fragment: SignBeaconTransactionFragment)
}
