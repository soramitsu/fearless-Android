package jp.co.soramitsu.app.navigation.onboarding.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.app.navigation.onboarding.OnboardingFragment
import jp.co.soramitsu.common.di.scope.ScreenScope

@Subcomponent(
    modules = [
        OnboardingModule::class
    ]
)
@ScreenScope
interface OnboardingComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): OnboardingComponent
    }

    fun inject(onboardingFragment: OnboardingFragment)
}