package jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.custom.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.custom.CustomRebondFragment

@Subcomponent(
    modules = [
        CustomRebondModule::class
    ]
)
@ScreenScope
interface CustomRebondComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
        ): CustomRebondComponent
    }

    fun inject(fragment: CustomRebondFragment)
}
