package jp.co.soramitsu.featureaccountimpl.presentation.profile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.featureaccountapi.domain.interfaces.AccountRepository
import jp.co.soramitsu.featureaccountapi.domain.interfaces.GetTotalBalanceUseCase
import jp.co.soramitsu.featureaccountimpl.domain.GetTotalBalanceUseCaseImpl
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@InstallIn(SingletonComponent::class)
@Module(includes = [ViewModelModule::class])
class ProfileModule {

    @Provides
    fun provideGetTotalBalanceUseCase(accountRepository: AccountRepository, chainRegistry: ChainRegistry, assetDao: AssetDao): GetTotalBalanceUseCase {
        return GetTotalBalanceUseCaseImpl(accountRepository, chainRegistry, assetDao)
    }
}
