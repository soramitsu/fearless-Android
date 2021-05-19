package jp.co.soramitsu.feature_crowdloan_impl.presentation.main.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.CrowdloanInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.main.CrowdloanViewModel

@Module(includes = [ViewModelModule::class])
class CrowdloanModule {

    @Provides
    @IntoMap
    @ViewModelKey(CrowdloanViewModel::class)
    fun provideViewModel(
        interactor: CrowdloanInteractor,
    ): ViewModel {
        return CrowdloanViewModel(
            interactor,
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): CrowdloanViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(CrowdloanViewModel::class.java)
    }
}
