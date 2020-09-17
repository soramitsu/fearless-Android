package jp.co.soramitsu.feature_account_impl.presentation.account.list.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.account.list.AccountListFragment

@Subcomponent(
    modules = [
        AccountListModule::class
    ]
)
@ScreenScope
interface AccountListComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): AccountListComponent
    }

    fun inject(fragment: AccountListFragment)
}