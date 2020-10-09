package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.common.AddressIconGenerator
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft
import jp.co.soramitsu.feature_wallet_impl.presentation.send.confirm.ConfirmTransferViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.TransactionDetailViewModel

@Module(includes = [ViewModelModule::class])
class TransactionDetailModule {

    @Provides
    @IntoMap
    @ViewModelKey(TransactionDetailViewModel::class)
    fun provideViewModel(
        interactor: WalletInteractor,
        router: WalletRouter,
        resourceManager: ResourceManager,
        addressIconGenerator: AddressIconGenerator,
        clipboardManager: ClipboardManager,
        transactionModel: TransactionModel
    ): ViewModel {
        return TransactionDetailViewModel(
            interactor,
            router,
            resourceManager,
            addressIconGenerator,
            clipboardManager,
            transactionModel
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): TransactionDetailViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(TransactionDetailViewModel::class.java)
    }
}