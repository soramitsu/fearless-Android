package jp.co.soramitsu.feature_account_impl.presentation.account.export.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.di.modules.Caching
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.GetTotalBalanceUseCase
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_impl.domain.GetTotalBalanceUseCaseImpl
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.export.WalletExportViewModel
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class WalletExportModule {

    @Provides
    @IntoMap
    @ViewModelKey(WalletExportViewModel::class)
    fun provideViewModel(
        interactor: AccountDetailsInteractor,
        router: AccountRouter,
        resourceManager: ResourceManager,
        @Caching
        iconGenerator: AddressIconGenerator,
        metaId: Long,
        getTotalBalance: GetTotalBalanceUseCase,
        externalAccountActions: ExternalAccountActions.Presentation
    ): ViewModel {
        return WalletExportViewModel(
            interactor,
            router,
            iconGenerator,
            resourceManager,
            metaId,
            getTotalBalance,
            externalAccountActions
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): WalletExportViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(WalletExportViewModel::class.java)
    }

    @Provides
    fun provideGetTotalBalanceUseCase(accountRepository: AccountRepository, chainRegistry: ChainRegistry, assetDao: AssetDao): GetTotalBalanceUseCase {
        return GetTotalBalanceUseCaseImpl(accountRepository, chainRegistry, assetDao)
    }
}
