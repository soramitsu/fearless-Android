package jp.co.soramitsu.feature_account_impl.presentation.profile.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.GetTotalBalanceUseCase
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_impl.domain.GetTotalBalanceUseCaseImpl
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.profile.ProfileViewModel
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class ProfileModule {

    @Provides
    @IntoMap
    @ViewModelKey(ProfileViewModel::class)
    fun provideViewModel(
        interactor: AccountInteractor,
        router: AccountRouter,
        addressIconGenerator: AddressIconGenerator,
        externalAccountActions: ExternalAccountActions.Presentation,
        getTotalBalance: GetTotalBalanceUseCase,
        getAvailableFiatCurrencies: GetAvailableFiatCurrencies,
        selectedFiat: SelectedFiat
    ): ViewModel {
        return ProfileViewModel(
            interactor,
            router,
            addressIconGenerator,
            externalAccountActions,
            getTotalBalance,
            getAvailableFiatCurrencies,
            selectedFiat
        )
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ProfileViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ProfileViewModel::class.java)
    }

    @Provides
    fun provideGetTotalBalanceUseCase(accountRepository: AccountRepository, chainRegistry: ChainRegistry, assetDao: AssetDao): GetTotalBalanceUseCase {
        return GetTotalBalanceUseCaseImpl(accountRepository, chainRegistry, assetDao)
    }
}
