package jp.co.soramitsu.feature_account_impl.presentation.account.create.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_api.domain.model.Node
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
            @BindsInstance networkType: Node.NetworkType?
        ): CreateAccountComponent
    }

    fun inject(createAccountFragment: CreateAccountFragment)
}