package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicFragment
import javax.inject.Named

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
            @BindsInstance fragment: Fragment,
            @BindsInstance mnemonic: List<String>,
            @BindsInstance accountName: String,
            @BindsInstance cryptoType: CryptoType,
            @BindsInstance node: Node,
            @BindsInstance @Named("derivation_path") derivationPath: String
        ): ConfirmMnemonicComponent
    }

    fun inject(confirmMnemonicFragment: ConfirmMnemonicFragment)
}