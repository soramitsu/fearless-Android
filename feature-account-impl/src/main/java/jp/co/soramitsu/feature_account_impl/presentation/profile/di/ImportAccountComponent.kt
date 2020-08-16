package jp.co.soramitsu.feature_account_impl.presentation.importing.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.importing.ImportAccountFragment
import jp.co.soramitsu.feature_account_impl.presentation.profile.di.di.ImportAccountModule

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
            @BindsInstance fragment: Fragment
        ): ImportAccountComponent
    }

    fun inject(importAccountFragment: ImportAccountFragment)
}