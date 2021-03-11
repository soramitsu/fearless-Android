package jp.co.soramitsu.feature_account_impl.presentation.node.list.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.node.list.NodesFragment

@Subcomponent(
    modules = [
        NodesModule::class
    ]
)
@ScreenScope
interface NodesComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): NodesComponent
    }

    fun inject(fragment: NodesFragment)
}