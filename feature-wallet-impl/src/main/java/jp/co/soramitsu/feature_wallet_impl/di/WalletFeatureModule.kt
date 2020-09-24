package jp.co.soramitsu.feature_wallet_impl.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.rpc.RxWebSocket
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_impl.data.network.source.WssSubstrateSource
import jp.co.soramitsu.feature_wallet_impl.data.repository.WalletRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.domain.WalletInteractorImpl

@Module
class WalletFeatureModule {
    @Provides
    @FeatureScope
    fun provideRxWebSocket(mapper: Gson) = RxWebSocket(mapper)

    @Provides
    @FeatureScope
    fun provideSubstrateSource(rxWebSocket: RxWebSocket) = WssSubstrateSource(rxWebSocket)

    @Provides
    @FeatureScope
    fun provideJsonMapper() = Gson()

    @Provides
    @FeatureScope
    fun provideWalletRepository(
        substrateSource: WssSubstrateSource,
        accountRepository: AccountRepository,
        assetDao: AssetDao
    ): WalletRepository = WalletRepositoryImpl(substrateSource, accountRepository, assetDao)

    @Provides
    @FeatureScope
    fun provideWalletInteractor(repository: WalletRepository): WalletInteractor = WalletInteractorImpl(repository)
}