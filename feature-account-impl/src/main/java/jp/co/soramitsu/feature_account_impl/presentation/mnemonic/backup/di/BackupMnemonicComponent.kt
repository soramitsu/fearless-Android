package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.BackupMnemonicFragment

@Subcomponent(
    modules = [
        BackupMnemonicModule::class
    ]
)
@ScreenScope
interface BackupMnemonicComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance accountName: String,
            @BindsInstance selectedNetworkType: Node.NetworkType
        ): BackupMnemonicComponent
    }

    fun inject(backupMnemonicFragment: BackupMnemonicFragment)
}