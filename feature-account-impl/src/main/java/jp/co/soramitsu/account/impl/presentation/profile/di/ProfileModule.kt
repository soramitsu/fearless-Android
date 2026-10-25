package jp.co.soramitsu.account.impl.presentation.profile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import javax.inject.Singleton
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.AssetBalanceUseCase
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.impl.domain.AssetBalanceUseCaseImpl
import jp.co.soramitsu.account.impl.domain.TotalBalanceUseCaseImpl
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetReader

@InstallIn(SingletonComponent::class)
@Module(includes = [ViewModelModule::class])
class ProfileModule {

    @Provides
    fun provideAssetBalanceUseCase(reader: CurrentAccountAssetReader, portfolioVisibility: PortfolioVisibility): AssetBalanceUseCase {
        return AssetBalanceUseCaseImpl(reader, portfolioVisibility)
    }

    @Provides
    @Singleton
    fun provideCurrentAccountAssetReader(
        assetDao: AssetDao,
        chainsRepository: ChainsRepository,
        accountRepository: AccountRepository,
        tokenPriceDao: TokenPriceDao,
        selectedFiat: SelectedFiat
    ): CurrentAccountAssetReader = CurrentAccountAssetReader(
        assetDao, chainsRepository, accountRepository, tokenPriceDao, selectedFiat
    )

    @Provides
    fun provideTotalBalanceUseCase(
        accountRepository: AccountRepository,
        reader: CurrentAccountAssetReader,
        portfolioVisibility: PortfolioVisibility
    ): TotalBalanceUseCase {
        return TotalBalanceUseCaseImpl(accountRepository, reader, portfolioVisibility)
    }
}
