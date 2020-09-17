package jp.co.soramitsu.feature_account_impl.presentation.account.details.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.account.details.AccountDetailsFragment

@Subcomponent(
    modules = [
        AccountDetailsModule::class
    ]
)
@ScreenScope
interface AccountDetailsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance accountAddress: String
        ): AccountDetailsComponent
    }

    fun inject(fragment: AccountDetailsFragment)
}