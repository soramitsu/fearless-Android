package jp.co.soramitsu.feature_onboarding_impl.presentation.terms.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_onboarding_impl.presentation.terms.TermsFragment

@Subcomponent(
    modules = [
        TermsModule::class
    ]
)
@ScreenScope
interface TermsComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): TermsComponent
    }

    fun inject(termsFragment: TermsFragment)
}