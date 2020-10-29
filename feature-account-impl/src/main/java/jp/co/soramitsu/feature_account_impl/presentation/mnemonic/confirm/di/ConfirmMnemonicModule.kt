package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicViewModel

@Module(includes = [ViewModelModule::class])
class ConfirmMnemonicModule {

    @Provides
    @IntoMap
    @ViewModelKey(ConfirmMnemonicViewModel::class)
    fun provideViewModel(
        interactor: AccountInteractor,
        router: AccountRouter,
        resourceManager: ResourceManager,
        deviceVibrator: DeviceVibrator,
        payload: ConfirmMnemonicPayload
    ): ViewModel {
        return ConfirmMnemonicViewModel(interactor, router, resourceManager, deviceVibrator, payload)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ConfirmMnemonicViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ConfirmMnemonicViewModel::class.java)
    }
}