package jp.co.soramitsu.feature_account_impl.presentation.node.details.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.node.details.NodeDetailsFragment

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
            @BindsInstance nodeId: Int,
            @BindsInstance isSelected: Boolean
        ): NodeDetailsComponent
    }

    fun inject(fragment: NodeDetailsFragment)
}