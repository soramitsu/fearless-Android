package jp.co.soramitsu.feature_account_impl.presentation.account.export.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.account.export.WalletExportFragment

@Subcomponent(
    modules = [
        WalletExportModule::class
    ]
)
@ScreenScope
interface WalletExportComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance metaId: Long
        ): WalletExportComponent
    }

    fun inject(fragment: WalletExportFragment)
}
