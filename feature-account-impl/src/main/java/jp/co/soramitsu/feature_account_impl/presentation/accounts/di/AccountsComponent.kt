package jp.co.soramitsu.feature_account_impl.presentation.accounts.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.accounts.AccountsFragment
import jp.co.soramitsu.feature_account_impl.presentation.importing.ImportAccountFragment

@Subcomponent(
    modules = [
        AccountsModule::class
    ]
)
@ScreenScope
interface AccountsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): AccountsComponent
    }

    fun inject(fragment: AccountsFragment)
}