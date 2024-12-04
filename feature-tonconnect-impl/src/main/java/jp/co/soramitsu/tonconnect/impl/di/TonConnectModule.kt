package jp.co.soramitsu.tonconnect.impl.di

import co.jp.soramitsu.tonconnect.domain.TonConnectInteractor
import co.jp.soramitsu.tonconnect.domain.TonConnectRepository
import co.jp.soramitsu.tonconnect.domain.TonConnectRouter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.coredb.dao.TonConnectDao
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.tonconnect.impl.data.TonConnectRepositoryImpl
import jp.co.soramitsu.tonconnect.impl.domain.TonConnectInteractorImpl
import okhttp3.OkHttpClient
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class TonConnectModule {
    @Singleton
    @Provides
    fun provideTonConnectInteractor(
        chainsRepository: ChainsRepository,
        accountRepository: AccountRepository,
        keypairProvider: KeypairProvider,
        tonApi: TonApi,
        @Named("TonSseClient") tonSseClient: OkHttpClient,
        @Named("tonApiHttpClient") tonApiClient: OkHttpClient,
        tonConnectRouter: TonConnectRouter,
        tonConnectRepository: TonConnectRepository,
        tonRemoteSource: TonRemoteSource,
        contextManager: ContextManager
    ): TonConnectInteractor = TonConnectInteractorImpl(
        chainsRepository,
        accountRepository,
        keypairProvider,
        tonApi,
        tonConnectRouter,
        tonConnectRepository,
        tonRemoteSource,
        contextManager,
        tonSseClient,
        tonApiClient
    )

    @Provides
    fun provideTonConnectRepo(
        tonConnectDao: TonConnectDao,
        encryptedPreferences: EncryptedPreferences
    ): TonConnectRepository =
        TonConnectRepositoryImpl(tonConnectDao, encryptedPreferences)
}
