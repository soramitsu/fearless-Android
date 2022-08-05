package jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryProvider

@InstallIn(SingletonComponent::class)
@Module(includes = [ViewModelModule::class])
class BalanceDetailModule {

    @Provides
    fun provideTransferHistoryMixin(
        walletInteractor: WalletInteractor,
        addressIconGenerator: AddressIconGenerator,
        walletRouter: WalletRouter,
        historyFiltersProvider: HistoryFiltersProvider,
        resourceManager: ResourceManager,
        addressDisplayUseCase: AddressDisplayUseCase
    ): TransactionHistoryMixin {
        return TransactionHistoryProvider(
            walletInteractor,
            addressIconGenerator,
            walletRouter,
            historyFiltersProvider,
            resourceManager,
            addressDisplayUseCase
        )
    }
}
