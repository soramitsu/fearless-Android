package jp.co.soramitsu.feature_account_impl.presentation.exporting.mnemonic.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.exporting.mnemonic.ExportMnemonicFragment

@Subcomponent(
    modules = [
        ExportMnemonicModule::class
    ]
)
@ScreenScope
interface ExportMnemonicComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance accountAddress: String
        ): ExportMnemonicComponent
    }

    fun inject(fragment: ExportMnemonicFragment)
}