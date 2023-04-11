package jp.co.soramitsu.soracard.impl.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.coredb.dao.SoraCardDao
import jp.co.soramitsu.soracard.api.domain.BuyCryptoDataSource
import jp.co.soramitsu.soracard.api.domain.BuyCryptoRepository
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.api.domain.SoraCardRepository
import jp.co.soramitsu.soracard.impl.data.SoraCardApi
import jp.co.soramitsu.soracard.impl.domain.BuyCryptoDataSourceImpl
import jp.co.soramitsu.soracard.impl.domain.BuyCryptoRepositoryImpl
import jp.co.soramitsu.soracard.impl.domain.SoraCardInteractorImpl
import jp.co.soramitsu.soracard.impl.domain.SoraCardRepositoryImpl
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuHttpClientProvider
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuHttpClientProviderImpl
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
interface SoraCardFeatureBindModule {
    @Binds
    @Singleton
    fun bindsSoraCardInteractor(soraCardInteractor: SoraCardInteractorImpl): SoraCardInteractor

    @Binds
    fun bindsSoraCardRepository(soraCardRepository: SoraCardRepositoryImpl): SoraCardRepository
}

@InstallIn(SingletonComponent::class)
@Module(includes = [SoraCardFeatureBindModule::class])
class SoraCardFeatureModule {

    @Provides
    fun providesSoraCardApi(networkApiCreator: NetworkApiCreator): SoraCardApi {
        return networkApiCreator.create(SoraCardApi::class.java)
    }

    @Provides
    fun provideSoraCardRepositoryImpl(
        soraCardDao: SoraCardDao,
        soraCardApi: SoraCardApi
    ): SoraCardRepositoryImpl {
        return SoraCardRepositoryImpl(soraCardDao, soraCardApi)
    }

    @Provides
    fun provideBuyCryptoRepository(
        dataSource: BuyCryptoDataSource
    ): BuyCryptoRepository = BuyCryptoRepositoryImpl(
        dataSource
    )

    @Provides
    fun provideBuyCryptoDataSource(
        clientProvider: SoramitsuHttpClientProvider
    ): BuyCryptoDataSource =
        BuyCryptoDataSourceImpl(clientProvider)

    @Provides
    fun provideSoramitsuHttpClientProvider(): SoramitsuHttpClientProvider =
        SoramitsuHttpClientProviderImpl()
}
