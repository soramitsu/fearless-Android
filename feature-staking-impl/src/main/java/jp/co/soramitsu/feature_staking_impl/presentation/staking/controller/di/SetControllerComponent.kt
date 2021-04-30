package jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.SetControllerFragment


@Subcomponent(
    modules = [
        SetControllerModule::class
    ]
)
@ScreenScope
interface SetControllerComponent {

    @Subcomponent.Factory
    interface Factory {
        fun create(@BindsInstance fragment: Fragment): SetControllerComponent
    }

    fun inject(fragment: SetControllerFragment)
}
