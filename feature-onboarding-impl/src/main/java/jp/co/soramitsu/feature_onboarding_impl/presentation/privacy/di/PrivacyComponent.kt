package jp.co.soramitsu.feature_onboarding_impl.presentation.privacy.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_onboarding_impl.presentation.privacy.PrivacyFragment

@Subcomponent(
    modules = [
        PrivacyModule::class
    ]
)
@ScreenScope
interface PrivacyComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): PrivacyComponent
    }

    fun inject(privacyFragment: PrivacyFragment)
}