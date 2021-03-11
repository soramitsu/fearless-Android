package jp.co.soramitsu.feature_account_impl.presentation.about.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.about.AboutFragment

@Subcomponent(
    modules = [
        AboutModule::class
    ]
)
@ScreenScope
interface AboutComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): AboutComponent
    }

    fun inject(aboutFragment: AboutFragment)
}