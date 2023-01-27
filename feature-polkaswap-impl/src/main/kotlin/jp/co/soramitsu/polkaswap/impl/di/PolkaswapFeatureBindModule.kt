package jp.co.soramitsu.polkaswap.impl.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import jp.co.soramitsu.account.api.extrinsic.ExtrinsicService
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.impl.data.PolkaswapRepositoryImpl
import jp.co.soramitsu.polkaswap.impl.domain.PolkaswapInteractorImpl
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.rpc.RpcCalls
import jp.co.soramitsu.runtime.storage.source.StorageDataSource

@InstallIn(SingletonComponent::class)
@Module
interface PolkaswapFeatureBindModule {

    @Binds
    @Singleton
    fun bindsPolkaswapInteractor(polkaswapInteractor: PolkaswapInteractorImpl): PolkaswapInteractor

    @Binds
    fun bindsPolkaswapRepository(polkaswapRepository: PolkaswapRepositoryImpl): PolkaswapRepository
}

@InstallIn(SingletonComponent::class)
@Module(includes = [PolkaswapFeatureBindModule::class])
class PolkaswapFeatureModule {

    @Provides
    fun providePolkaswapRepositoryImpl(
        remoteConfigFetcher: RemoteConfigFetcher,
        @Named(REMOTE_STORAGE_SOURCE) remoteSource: StorageDataSource,
        extrinsicService: ExtrinsicService,
        chainRegistry: ChainRegistry,
        rpcCalls: RpcCalls
    ): PolkaswapRepositoryImpl {
        return PolkaswapRepositoryImpl(remoteConfigFetcher, remoteSource, extrinsicService, chainRegistry, rpcCalls)
    }
}
