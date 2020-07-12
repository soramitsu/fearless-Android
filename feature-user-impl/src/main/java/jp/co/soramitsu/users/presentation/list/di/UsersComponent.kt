package jp.co.soramitsu.users.presentation.list.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.users.presentation.list.UsersFragment

@Subcomponent(
    modules = [
        UsersModule::class
    ]
)
@ScreenScope
interface UsersComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): UsersComponent
    }

    fun inject(fragment: UsersFragment)
}