package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.GetTotalBalanceUseCase
import jp.co.soramitsu.feature_account_impl.domain.GetTotalBalanceUseCaseImpl
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.domain.beacon.BeaconInteractor
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main.BeaconViewModel
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class BeaconModule {

    @Provides
    @IntoMap
    @ViewModelKey(BeaconViewModel::class)
    fun provideViewModel(
        beaconInteractor: BeaconInteractor,
        router: WalletRouter,
        interactor: WalletInteractor,
        iconAddressIconGenerator: AddressIconGenerator,
        resourceManager: ResourceManager,
        totalBalance: GetTotalBalanceUseCase,
        qrContent: String?
    ): ViewModel {
        return BeaconViewModel(
            beaconInteractor,
            router,
            interactor,
            iconAddressIconGenerator,
            resourceManager,
            totalBalance,
            qrContent
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): BeaconViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(BeaconViewModel::class.java)
    }

    @Provides
    fun provideGetTotalBalanceUseCase(accountRepository: AccountRepository, chainRegistry: ChainRegistry, assetDao: AssetDao): GetTotalBalanceUseCase {
        return GetTotalBalanceUseCaseImpl(accountRepository, chainRegistry, assetDao)
    }
}
