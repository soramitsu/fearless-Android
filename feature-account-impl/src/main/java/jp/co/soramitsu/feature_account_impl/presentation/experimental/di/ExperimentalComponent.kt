package jp.co.soramitsu.feature_account_impl.presentation.experimental.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.experimental.ExperimentalFragment

@Subcomponent(
    modules = [
        ExperimentalModule::class
    ]
)
@ScreenScope
interface ExperimentalComponent {
    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): ExperimentalComponent
    }

    fun inject(profileFragment: ExperimentalFragment)
}
