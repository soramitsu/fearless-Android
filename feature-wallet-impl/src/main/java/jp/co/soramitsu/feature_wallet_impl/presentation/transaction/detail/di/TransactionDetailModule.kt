package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.presenatation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.transfer.TransactionDetailViewModel
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

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
        addressDisplayUseCase: AddressDisplayUseCase,
        operation: OperationParcelizeModel.Transfer,
        chainRegistry: ChainRegistry,
        assetPayload: AssetPayload
    ): ViewModel {
        return TransactionDetailViewModel(
            interactor,
            router,
            resourceManager,
            addressIconGenerator,
            clipboardManager,
            addressDisplayUseCase,
            chainRegistry,
            operation,
            assetPayload
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
