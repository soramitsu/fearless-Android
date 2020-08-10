package jp.co.soramitsu.feature_main_impl.presentation.pincode.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.pincode.PincodeFragment

@Subcomponent(
    modules = [
        PinCodeModule::class
    ]
)
@ScreenScope
interface PinCodeComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        @BindsInstance
        fun withMaxPinCodeLength(maxPinCodeLength: Int): Builder

        fun build(): PinCodeComponent
    }

    fun inject(pinCodeFragment: PincodeFragment)
}