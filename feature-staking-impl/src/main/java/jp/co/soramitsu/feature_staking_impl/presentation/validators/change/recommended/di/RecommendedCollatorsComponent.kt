package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.recommended.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.recommended.RecommendedCollatorsFragment

@Subcomponent(
    modules = [
        RecommendedCollatorsModule::class
    ]
)
@ScreenScope
interface RecommendedCollatorsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): RecommendedCollatorsComponent
    }

    fun inject(fragment: RecommendedCollatorsFragment)
}
