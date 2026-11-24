package jp.co.soramitsu.tonconnect.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.coredb.dao.TonConnectDao
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRepository
import jp.co.soramitsu.tonconnect.impl.data.PreferencesBridgeEventLedgerStorage
import jp.co.soramitsu.tonconnect.impl.data.TonConnectBridgeEventLedger
import jp.co.soramitsu.tonconnect.impl.data.TonConnectRepositoryImpl
import jp.co.soramitsu.tonconnect.impl.domain.TonConnectInteractorImpl
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class TonConnectModule {
    @Suppress("LongParameterList")
    @Singleton
    @Provides
    fun provideTonConnectInteractor(
        chainsRepository: ChainsRepository,
        accountRepository: AccountRepository,
        keypairProvider: KeypairProvider,
        tonApi: TonApi,
        @Named("TonSseClient") tonSseClient: OkHttpClient,
        @Named("tonApiHttpClient") tonApiClient: OkHttpClient,
        tonConnectRepository: TonConnectRepository,
        tonRemoteSource: TonRemoteSource,
        keypairRepository: KeypairProvider,
        bridgeEventLedger: TonConnectBridgeEventLedger
    ): TonConnectInteractor = TonConnectInteractorImpl(
        chainsRepository,
        accountRepository,
        keypairProvider,
        tonApi,
        tonConnectRepository,
        tonRemoteSource,
        keypairRepository,
        tonSseClient,
        tonApiClient,
        bridgeEventLedger
    )

    @Singleton
    @Provides
    fun provideTonConnectRepo(
        tonConnectDao: TonConnectDao,
        encryptedPreferences: EncryptedPreferences
    ): TonConnectRepository = TonConnectRepositoryImpl(tonConnectDao, encryptedPreferences)

    @Singleton
    @Provides
    fun provideBridgeEventLedger(preferences: Preferences): TonConnectBridgeEventLedger =
        TonConnectBridgeEventLedger(PreferencesBridgeEventLedgerStorage(preferences))
}
