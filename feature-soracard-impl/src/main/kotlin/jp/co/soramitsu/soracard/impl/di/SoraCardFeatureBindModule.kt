package jp.co.soramitsu.soracard.impl.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.oauth.network.SoraCardNetworkClient
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.impl.domain.SoraCardInteractorImpl
import jp.co.soramitsu.soracard.impl.domain.SoraCardNetworkClientImpl
import jp.co.soramitsu.soracard.impl.domain.SoraCardRetrofitClient
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
interface SoraCardFeatureBindModule {
    @Binds
    @Singleton
    fun bindsSoraCardInteractor(soraCardInteractor: SoraCardInteractorImpl): SoraCardInteractor
}

@InstallIn(SingletonComponent::class)
@Module(includes = [SoraCardFeatureBindModule::class])
class SoraCardFeatureModule {

    @Provides
    fun provideSoraCardNetworkClient(
        networkApiCreator: NetworkApiCreator,
        json: Json,
    ): SoraCardNetworkClient {
        return SoraCardNetworkClientImpl(
            retrofitClient = networkApiCreator.create(SoraCardRetrofitClient::class.java),
            json = json,
        )
    }
}
