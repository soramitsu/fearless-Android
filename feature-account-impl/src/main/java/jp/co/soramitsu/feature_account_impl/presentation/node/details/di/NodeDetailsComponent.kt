package jp.co.soramitsu.feature_account_impl.presentation.node.details.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.node.details.NodeDetailsFragment
import jp.co.soramitsu.feature_account_impl.presentation.node.details.NodeDetailsPayload

@Subcomponent(
    modules = [
        NodeDetailsModule::class
    ]
)
@ScreenScope
interface NodeDetailsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance nodeId: NodeDetailsPayload
        ): NodeDetailsComponent
    }

    fun inject(fragment: NodeDetailsFragment)
}
