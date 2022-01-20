package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm.ExportJsonConfirmPayload
import jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm.ExportJsonConfirmViewModel
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class ExportJsonConfirmModule {

    @Provides
    @IntoMap
    @ViewModelKey(ExportJsonConfirmViewModel::class)
    fun provideViewModel(
        router: AccountRouter,
        resourceManager: ResourceManager,
        accountInteractor: AccountInteractor,
        chainRegistry: ChainRegistry,
        payload: ExportJsonConfirmPayload
    ): ViewModel {
        return ExportJsonConfirmViewModel(router, resourceManager, accountInteractor, chainRegistry, payload)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ExportJsonConfirmViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ExportJsonConfirmViewModel::class.java)
    }
}
