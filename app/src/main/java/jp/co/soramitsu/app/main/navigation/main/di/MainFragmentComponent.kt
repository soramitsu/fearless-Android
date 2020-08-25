package jp.co.soramitsu.app.main.navigation.main.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.app.main.navigation.main.MainFragment
import jp.co.soramitsu.common.di.scope.ScreenScope

@Subcomponent(
    modules = [
        MainModule::class
    ]
)
@ScreenScope
interface MainFragmentComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): MainFragmentComponent
    }

    fun inject(mainFragment: MainFragment)
}