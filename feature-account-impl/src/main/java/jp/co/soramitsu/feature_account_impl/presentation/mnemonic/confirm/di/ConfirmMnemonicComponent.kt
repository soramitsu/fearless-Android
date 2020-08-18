package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicFragment

@Subcomponent(
    modules = [
        ConfirmMnemonicModule::class
    ]
)
@ScreenScope
interface ConfirmMnemonicComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): ConfirmMnemonicComponent
    }

    fun inject(confirmMnemonicFragment: ConfirmMnemonicFragment)
}