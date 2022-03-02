package jp.co.soramitsu.feature_account_impl.presentation.account.exportaccounts.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.account.exportaccounts.AccountsForExportFragment
import jp.co.soramitsu.feature_account_impl.presentation.account.exportaccounts.AccountsForExportPayload

@Subcomponent(
    modules = [
        AccountsForExportModule::class
    ]
)
@ScreenScope
interface AccountsForExportComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance payload: AccountsForExportPayload
        ): AccountsForExportComponent
    }

    fun inject(fragment: AccountsForExportFragment)
}
