package jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail.di

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
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail.BalanceDetailViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryProvider

@Module(includes = [ViewModelModule::class])
class BalanceDetailModule {

    @Provides
    @ScreenScope
    fun provideTransferHistoryMixin(walletInteractor: WalletInteractor): TransactionHistoryMixin {
        return TransactionHistoryProvider(walletInteractor)
    }

    @Provides
    @IntoMap
    @ViewModelKey(BalanceDetailViewModel::class)
    fun provideViewModel(
        interactor: WalletInteractor,
        router: WalletRouter,
        iconGenerator: IconGenerator,
        transactionHistoryMixin: TransactionHistoryMixin,
        token: Asset.Token
    ): ViewModel {
        return BalanceDetailViewModel(interactor, iconGenerator, router, token, transactionHistoryMixin)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): BalanceDetailViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(BalanceDetailViewModel::class.java)
    }
}