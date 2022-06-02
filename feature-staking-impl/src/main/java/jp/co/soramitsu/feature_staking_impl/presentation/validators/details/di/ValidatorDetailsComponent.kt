package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.CollatorDetailsFragment
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.ValidatorDetailsFragment
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel

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
            @BindsInstance validator: ValidatorDetailsParcelModel
        ): ValidatorDetailsComponent
    }

    fun inject(fragment: ValidatorDetailsFragment)
}

@Subcomponent(
    modules = [
        CollatorDetailsModule::class
    ]
)
@ScreenScope
interface CollatorDetailsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance collator: CollatorDetailsParcelModel
        ): CollatorDetailsComponent
    }

    fun inject(fragment: CollatorDetailsFragment)
}
