package jp.co.soramitsu.app.root.presentation.main.extrinsic_builder

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule

@Module(includes = [ViewModelModule::class])
class ExtrinsicBuilderModule {

    @Provides
    @IntoMap
    @ViewModelKey(ExtrinsicBuilderViewModel::class)
    fun provideViewModel(
        extrinsicBuilderInteractor: ExtrinsicBuilderInteractor,
    ): ViewModel {
        return ExtrinsicBuilderViewModel(extrinsicBuilderInteractor)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory,
    ): ExtrinsicBuilderViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ExtrinsicBuilderViewModel::class.java)
    }
}
