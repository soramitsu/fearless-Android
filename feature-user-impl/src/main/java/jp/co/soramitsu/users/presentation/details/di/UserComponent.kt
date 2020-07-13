package jp.co.soramitsu.users.presentation.details.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.users.presentation.details.UserFragment

@Subcomponent(
    modules = [
        UserModule::class
    ]
)
@ScreenScope
interface UserComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance userId: Int
        ): UserComponent
    }

    fun inject(fragment: UserFragment)
}