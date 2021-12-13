package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.chainId
import jp.co.soramitsu.feature_account_api.presenatation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.BalanceListViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryProvider

@Module(includes = [ViewModelModule::class])
class BalanceListModule {

    @Provides
    @ScreenScope
    fun provideTransferHistoryMixin(
        walletInteractor: WalletInteractor,
        addressIconGenerator: AddressIconGenerator,
        walletRouter: WalletRouter,
        historyFiltersProvider: HistoryFiltersProvider,
        resourceManager: ResourceManager,
        addressDisplayUseCase: AddressDisplayUseCase,
    ): TransactionHistoryMixin {
        return TransactionHistoryProvider(
            walletInteractor,
            addressIconGenerator,
            walletRouter,
            historyFiltersProvider,
            resourceManager,
            addressDisplayUseCase,
            chainId = Node.NetworkType.POLKADOT.chainId, // TODO remove transfer history provider from main screen
            assetId = "0"
        )
    }

    @Provides
    @IntoMap
    @ViewModelKey(BalanceListViewModel::class)
    fun provideViewModel(
        interactor: WalletInteractor,
        router: WalletRouter,
        addressIconGenerator: AddressIconGenerator,
    ): ViewModel {

        return BalanceListViewModel(
            interactor,
            addressIconGenerator,
            router
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory,
    ): BalanceListViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(BalanceListViewModel::class.java)
    }
}
