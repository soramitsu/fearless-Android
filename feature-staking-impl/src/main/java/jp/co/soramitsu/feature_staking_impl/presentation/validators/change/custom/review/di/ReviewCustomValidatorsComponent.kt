package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.review.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.review.ReviewCustomValidatorsFragment

@Subcomponent(
    modules = [
        ReviewCustomValidatorsModule::class
    ]
)
@ScreenScope
interface ReviewCustomValidatorsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): ReviewCustomValidatorsComponent
    }

    fun inject(fragment: ReviewCustomValidatorsFragment)
}
