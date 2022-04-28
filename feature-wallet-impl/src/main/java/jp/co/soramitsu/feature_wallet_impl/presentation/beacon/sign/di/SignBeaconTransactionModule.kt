package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.sign.di

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
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main.DAppMetadataModel
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.sign.SignBeaconTransactionViewModel
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class SignBeaconTransactionModule {

    @Provides
    @IntoMap
    @ViewModelKey(SignBeaconTransactionViewModel::class)
    fun provideViewModel(
        beaconInteractor: BeaconInteractor,
        router: WalletRouter,
        interactor: WalletInteractor,
        iconAddressIconGenerator: AddressIconGenerator,
        payloadToSign: String,
        resourceManager: ResourceManager,
        dAppMetadataModel: DAppMetadataModel,
        totalBalanceUseCase: GetTotalBalanceUseCase
    ): ViewModel {
        return SignBeaconTransactionViewModel(
            beaconInteractor,
            router,
            interactor,
            iconAddressIconGenerator,
            payloadToSign,
            resourceManager,
            dAppMetadataModel,
            totalBalanceUseCase
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): SignBeaconTransactionViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(SignBeaconTransactionViewModel::class.java)
    }

    @Provides
    fun provideGetTotalBalanceUseCase(accountRepository: AccountRepository, chainRegistry: ChainRegistry, assetDao: AssetDao): GetTotalBalanceUseCase {
        return GetTotalBalanceUseCaseImpl(accountRepository, chainRegistry, assetDao)
    }
}
