package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.BalanceListViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.transactions.mixin.TransferHistoryMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.transactions.mixin.TransferHistoryProvider

@Module(includes = [ViewModelModule::class])
class BalanceListModule {

    @Provides
    @ScreenScope
    fun provideTransferHistoryMixin(walletInteractor: WalletInteractor): TransferHistoryMixin {
        return TransferHistoryProvider(walletInteractor)
    }

    @Provides
    @IntoMap
    @ViewModelKey(BalanceListViewModel::class)
    fun provideViewModel(
        interactor: WalletInteractor,
        router: WalletRouter,
        iconGenerator: IconGenerator,
        transferHistoryMixin: TransferHistoryMixin
    ): ViewModel {
        return BalanceListViewModel(interactor, iconGenerator, router, transferHistoryMixin)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): BalanceListViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(BalanceListViewModel::class.java)
    }
}