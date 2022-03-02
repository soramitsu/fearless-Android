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
import jp.co.soramitsu.feature_account_api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailsPayload
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class ExtrinsicDetailModule {
    @Provides
    @IntoMap
    @ViewModelKey(ExtrinsicDetailViewModel::class)
    fun provideViewModel(
        clipboardManager: ClipboardManager,
        resourceManager: ResourceManager,
        addressDisplayUseCase: AddressDisplayUseCase,
        addressIconGenerator: AddressIconGenerator,
        router: WalletRouter,
        chainRegistry: ChainRegistry,
        payload: ExtrinsicDetailsPayload
    ): ViewModel {
        return ExtrinsicDetailViewModel(
            clipboardManager,
            resourceManager,
            addressDisplayUseCase,
            addressIconGenerator,
            router,
            chainRegistry,
            payload
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ExtrinsicDetailViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ExtrinsicDetailViewModel::class.java)
    }
}
