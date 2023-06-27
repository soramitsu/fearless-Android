package jp.co.soramitsu.account.impl.presentation.profile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.impl.domain.TotalBalanceUseCaseImpl
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@InstallIn(SingletonComponent::class)
@Module(includes = [ViewModelModule::class])
class ProfileModule {

    @Provides
    fun provideTotalBalanceUseCase(accountRepository: AccountRepository, chainRegistry: ChainRegistry, assetDao: AssetDao): TotalBalanceUseCase {
        return TotalBalanceUseCaseImpl(accountRepository, chainRegistry, assetDao)
    }
}
