package jp.co.soramitsu.feature_account_impl.presentation.account.create.di

import androidx.annotation.Nullable
import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.feature_account_impl.presentation.account.create.CreateAccountFragment

@Subcomponent(
    modules = [
        CreateAccountModule::class
    ]
)
@ScreenScope
interface CreateAccountComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance @Nullable payload: ChainAccountCreatePayload?
        ): CreateAccountComponent
    }

    fun inject(createAccountFragment: CreateAccountFragment)
}
