package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.settings.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.settings.CustomValidatorsSettingsFragment

@Subcomponent(
    modules = [
        CustomValidatorsSettingsModule::class
    ]
)
@ScreenScope
interface CustomValidatorsSettingsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): CustomValidatorsSettingsComponent
    }

    fun inject(fragment: CustomValidatorsSettingsFragment)
}
