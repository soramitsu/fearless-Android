package jp.co.soramitsu.feature_onboarding_impl.presentation.welcome.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_onboarding_impl.presentation.welcome.WelcomeFragment
import jp.co.soramitsu.feature_onboarding_impl.presentation.welcome.WelcomeFragmentPayload

@Subcomponent(
    modules = [
        WelcomeModule::class
    ]
)
@ScreenScope
interface WelcomeComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance payload: WelcomeFragmentPayload
        ): WelcomeComponent
    }

    fun inject(welcomeFragment: WelcomeFragment)
}
