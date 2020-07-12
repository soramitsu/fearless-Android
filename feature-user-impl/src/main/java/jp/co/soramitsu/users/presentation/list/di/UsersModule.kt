package jp.co.soramitsu.users.presentation.list.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_user_api.domain.interfaces.UserInteractor
import jp.co.soramitsu.users.UsersRouter
import jp.co.soramitsu.users.presentation.list.UsersViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class UsersModule {

    @Provides
    fun provideMainViewModel(fragment: Fragment, factory: ViewModelProvider.Factory): UsersViewModel {
        return ViewModelProviders.of(fragment, factory).get(UsersViewModel::class.java)
    }

    @Provides
    @IntoMap
    @ViewModelKey(UsersViewModel::class)
    fun provideSignInViewModel(interactor: UserInteractor, router: UsersRouter): ViewModel {
        return UsersViewModel(interactor, router)
    }
}