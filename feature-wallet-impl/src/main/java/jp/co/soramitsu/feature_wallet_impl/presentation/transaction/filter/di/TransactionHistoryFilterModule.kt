package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.TransactionHistoryFilterViewModel

@Module(includes = [ViewModelModule::class])
class TransactionHistoryFilterModule {
    @Provides
    @IntoMap
    @ViewModelKey(TransactionHistoryFilterViewModel::class)
    fun provideViewModel(
        router: WalletRouter,
        provider: HistoryFiltersProvider
    ): ViewModel {
        return TransactionHistoryFilterViewModel(router, provider)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): TransactionHistoryFilterViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(TransactionHistoryFilterViewModel::class.java)
    }
}
