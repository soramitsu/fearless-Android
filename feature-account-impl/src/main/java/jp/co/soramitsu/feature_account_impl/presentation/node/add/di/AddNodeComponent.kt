package jp.co.soramitsu.feature_account_impl.presentation.node.add.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.node.add.AddNodeFragment

@Subcomponent(
    modules = [
        AddNodeModule::class
    ]
)
@ScreenScope
interface AddNodeComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): AddNodeComponent
    }

    fun inject(fragment: AddNodeFragment)
}