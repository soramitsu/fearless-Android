package jp.co.soramitsu.app.root.presentation.main.coming_soon.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.app.root.presentation.main.coming_soon.ComingSoonViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class ComingSoonModule {

    @Provides
    @IntoMap
    @ViewModelKey(ComingSoonViewModel::class)
    fun provideViewModel(
        appLinksProvider: AppLinksProvider
    ): ViewModel {
        return ComingSoonViewModel(appLinksProvider)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ComingSoonViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ComingSoonViewModel::class.java)
    }
}