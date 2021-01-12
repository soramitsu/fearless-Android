package jp.co.soramitsu.feature_account_impl.presentation.about.di

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.about.AboutViewModel

@Module(includes = [ViewModelModule::class])
class AboutModule {

    @Provides
    @IntoMap
    @ViewModelKey(AboutViewModel::class)
    fun provideViewModel(
        router: AccountRouter,
        context: Context,
        appLinksProvider: AppLinksProvider,
        resourceManager: ResourceManager
    ): ViewModel {
        return AboutViewModel(router, context, appLinksProvider, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): AboutViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(AboutViewModel::class.java)
    }
}