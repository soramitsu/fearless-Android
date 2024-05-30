package jp.co.soramitsu.polkaswap.impl.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.impl.data.PolkaswapRepositoryImpl
import jp.co.soramitsu.polkaswap.impl.domain.PolkaswapInteractorImpl
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository

@InstallIn(SingletonComponent::class)
@Module
class PolkaswapFeatureModule {

    @Provides
    fun providePolkaswapRepositoryImpl(
        remoteConfigFetcher: RemoteConfigFetcher,
        @Named(REMOTE_STORAGE_SOURCE) remoteSource: StorageDataSource,
        extrinsicService: ExtrinsicService,
        chainRegistry: ChainRegistry,
        rpcCalls: RpcCalls,
        accountRepository: AccountRepository
    ): PolkaswapRepository {
        return PolkaswapRepositoryImpl(remoteConfigFetcher, remoteSource, extrinsicService, chainRegistry, rpcCalls, accountRepository)
    }

    @Provides
    @Singleton
    fun providePolkaswapInteractor(
        chainRegistry: ChainRegistry,
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
        polkaswapRepository: PolkaswapRepository,
        sharedPreferences: Preferences,
        chainsRepository: ChainsRepository,
    ): PolkaswapInteractor {
        return PolkaswapInteractorImpl(
            chainRegistry,
            walletRepository,
            accountRepository,
            polkaswapRepository,
            sharedPreferences,
            chainsRepository
        )
    }
}
