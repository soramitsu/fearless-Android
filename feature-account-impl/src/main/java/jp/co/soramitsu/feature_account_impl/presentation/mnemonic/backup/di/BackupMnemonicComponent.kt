package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.BackupMnemonicFragment
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.BackupMnemonicPayload

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
            @BindsInstance payload: BackupMnemonicPayload
        ): BackupMnemonicComponent
    }

    fun inject(backupMnemonicFragment: BackupMnemonicFragment)
}
