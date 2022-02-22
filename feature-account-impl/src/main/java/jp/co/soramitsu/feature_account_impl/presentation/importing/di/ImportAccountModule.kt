package jp.co.soramitsu.feature_account_impl.presentation.importing.di

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.importing.FileReader
import jp.co.soramitsu.feature_account_api.presentation.importing.ImportAccountType
import jp.co.soramitsu.feature_account_impl.presentation.importing.ImportAccountViewModel

@Module(includes = [ViewModelModule::class])
class ImportAccountModule {

    @Provides
    @ScreenScope
    fun provideFileReader(context: Context) = FileReader(context)

    @Provides
    @IntoMap
    @ViewModelKey(ImportAccountViewModel::class)
    fun provideViewModel(
        interactor: AccountInteractor,
        router: AccountRouter,
        resourceManager: ResourceManager,
        cryptoChooserMixin: CryptoTypeChooserMixin,
        clipboardManager: ClipboardManager,
        fileReader: FileReader,
        blockChainType: ImportAccountType?,
        chainCreateAccountData: ChainAccountCreatePayload?
    ): ViewModel {
        return ImportAccountViewModel(
            interactor,
            router,
            resourceManager,
            cryptoChooserMixin,
            clipboardManager,
            fileReader,
            blockChainType,
            chainCreateAccountData
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ImportAccountViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ImportAccountViewModel::class.java)
    }
}
