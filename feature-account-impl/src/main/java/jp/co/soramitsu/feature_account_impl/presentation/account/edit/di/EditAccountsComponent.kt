package jp.co.soramitsu.feature_account_impl.presentation.account.edit.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.account.edit.EditAccountsFragment

@Subcomponent(
    modules = [
        EditAccountsModule::class
    ]
)
@ScreenScope
interface EditAccountsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): EditAccountsComponent
    }

    fun inject(fragment: EditAccountsFragment)
}