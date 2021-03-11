package jp.co.soramitsu.feature_staking_impl.presentation.validators.recommended.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.validators.recommended.RecommendedValidatorsFragment

@Subcomponent(
    modules = [
        RecommendedValidatorsModule::class
    ]
)
@ScreenScope
interface RecommendedValidatorsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): RecommendedValidatorsComponent
    }

    fun inject(fragment: RecommendedValidatorsFragment)
}
