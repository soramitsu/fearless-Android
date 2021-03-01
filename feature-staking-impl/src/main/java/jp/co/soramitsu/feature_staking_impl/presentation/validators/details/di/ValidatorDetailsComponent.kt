package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.ValidatorDetailsFragment
import jp.co.soramitsu.feature_staking_impl.presentation.validators.model.ValidatorDetailsModel

@Subcomponent(
    modules = [
        ValidatorDetailsModule::class
    ]
)
@ScreenScope
interface ValidatorDetailsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance validator: ValidatorDetailsModel
        ): ValidatorDetailsComponent
    }

    fun inject(fragment: ValidatorDetailsFragment)
}