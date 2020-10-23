package jp.co.soramitsu.app.root.presentation.main.coming_soon.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.app.root.presentation.main.coming_soon.ComingSoonFragment
import jp.co.soramitsu.common.di.scope.ScreenScope

@Subcomponent(
    modules = [
        ComingSoonModule::class
    ]
)
@ScreenScope
interface ComingSoonComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): ComingSoonComponent
    }

    fun inject(fragment: ComingSoonFragment)
}