package jp.co.soramitsu.feature_account_impl.presentation.experimental.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_account_impl.domain.BeaconConnectedUseCase
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.experimental.ExperimentalViewModel

@Module(includes = [ViewModelModule::class])
class ExperimentalModule {

    @Provides
    @IntoMap
    @ViewModelKey(ExperimentalViewModel::class)
    fun provideViewModel(beaconConnectedUseCase: BeaconConnectedUseCase, router: AccountRouter): ViewModel {
        return ExperimentalViewModel(router, beaconConnectedUseCase)
    }

    @Provides
    fun provideBeaconStatusUseCase(prefs: Preferences) = BeaconConnectedUseCase(prefs)

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ExperimentalViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ExperimentalViewModel::class.java)
    }
}
