package jp.co.soramitsu.polkaswap.impl.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.impl.data.PolkaswapRepositoryImpl
import jp.co.soramitsu.polkaswap.impl.domain.PolkaswapInteractorImpl
import jp.co.soramitsu.runtime.di.LOCAL_STORAGE_SOURCE
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.storage.source.LocalStorageSource
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.runtime.storage.source.StorageDataSource

@InstallIn(SingletonComponent::class)
@Module
interface PolkaswapFeatureBindModule {

    @Binds
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
        @Named(REMOTE_STORAGE_SOURCE) remoteSource: StorageDataSource
    ): PolkaswapRepositoryImpl {
        return PolkaswapRepositoryImpl(remoteConfigFetcher, remoteSource)
    }
}
