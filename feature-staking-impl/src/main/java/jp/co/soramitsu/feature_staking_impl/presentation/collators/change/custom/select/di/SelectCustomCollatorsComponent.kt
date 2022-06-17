package jp.co.soramitsu.feature_staking_impl.presentation.collators.change.custom.select.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.collators.change.custom.select.SelectCustomCollatorsFragment

@Subcomponent(
    modules = [
        SelectCustomCollatorsModule::class
    ]
)
@ScreenScope
interface SelectCustomCollatorsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): SelectCustomCollatorsComponent
    }

    fun inject(fragment: SelectCustomCollatorsFragment)
}
