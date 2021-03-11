package jp.co.soramitsu.feature_account_impl.presentation.account.edit.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.account.edit.AccountEditFragment

@Subcomponent(
    modules = [
        AccountEditModule::class
    ]
)
@ScreenScope
interface AccountEditComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): AccountEditComponent
    }

    fun inject(fragment: AccountEditFragment)
}