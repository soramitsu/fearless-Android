package jp.co.soramitsu.feature_account_impl.presentation.importing.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.presentation.importing.ImportAccountFragment

@Subcomponent(
    modules = [
        ImportAccountModule::class
    ]
)
@ScreenScope
interface ImportAccountComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance forcedNetworkType: Node.NetworkType?
        ): ImportAccountComponent
    }

    fun inject(importAccountFragment: ImportAccountFragment)
}