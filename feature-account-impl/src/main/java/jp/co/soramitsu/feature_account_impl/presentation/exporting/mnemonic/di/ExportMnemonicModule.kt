package jp.co.soramitsu.feature_account_impl.presentation.exporting.mnemonic.di

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.exporting.mnemonic.ExportMnemonicViewModel

@Module(includes = [ViewModelModule::class])
class ExportMnemonicModule {

    @Provides
    @IntoMap
    @ViewModelKey(ExportMnemonicViewModel::class)
    fun provideViewModel(router: AccountRouter, context: Context, resourceManager: ResourceManager): ViewModel {
        return ExportMnemonicViewModel(router, context, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ExportMnemonicViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ExportMnemonicViewModel::class.java)
    }
}