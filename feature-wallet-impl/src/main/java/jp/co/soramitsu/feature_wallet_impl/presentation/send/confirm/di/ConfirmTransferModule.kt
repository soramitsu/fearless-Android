package jp.co.soramitsu.feature_wallet_impl.presentation.send.confirm.di

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
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft
import jp.co.soramitsu.feature_wallet_impl.presentation.send.amount.ChooseAmountViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.send.confirm.ConfirmTransferViewModel

@Module(includes = [ViewModelModule::class])
class ConfirmTransferModule {

    @Provides
    @IntoMap
    @ViewModelKey(ConfirmTransferViewModel::class)
    fun provideViewModel(
        interactor: WalletInteractor,
        router: WalletRouter,
        resourceManager: ResourceManager,
        iconGenerator: IconGenerator,
        clipboardManager: ClipboardManager,
        transferDraft: TransferDraft
    ): ViewModel {
        return ConfirmTransferViewModel(
            interactor,
            router,
            resourceManager,
            iconGenerator,
            clipboardManager,
            transferDraft
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ConfirmTransferViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ConfirmTransferViewModel::class.java)
    }
}