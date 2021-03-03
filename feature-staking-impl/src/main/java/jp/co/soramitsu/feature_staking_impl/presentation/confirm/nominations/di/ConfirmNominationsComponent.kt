package jp.co.soramitsu.feature_staking_impl.presentation.confirm.nominations.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.confirm.nominations.ConfirmNominationsFragment

@Subcomponent(
    modules = [
        ConfirmNominationsModule::class
    ]
)
@ScreenScope
interface ConfirmNominationsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): ConfirmNominationsComponent
    }

    fun inject(fragment: ConfirmNominationsFragment)
}