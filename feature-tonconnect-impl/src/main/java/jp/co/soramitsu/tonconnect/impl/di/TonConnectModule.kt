package jp.co.soramitsu.tonconnect.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.coredb.dao.TonConnectDao
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRepository
import jp.co.soramitsu.tonconnect.impl.data.TonConnectRepositoryImpl
import jp.co.soramitsu.tonconnect.impl.domain.TonConnectInteractorImpl
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import okhttp3.OkHttpClient

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
        walletRepository: WalletRepository,
        keypairRepository: KeypairProvider
    ): TonConnectInteractor = TonConnectInteractorImpl(
        chainsRepository,
        accountRepository,
        keypairProvider,
        tonApi,
        tonConnectRepository,
        tonRemoteSource,
        walletRepository,
        keypairRepository,
        tonSseClient,
        tonApiClient
    )

    @Provides
    fun provideTonConnectRepo(
        tonConnectDao: TonConnectDao,
        encryptedPreferences: EncryptedPreferences
    ): TonConnectRepository = TonConnectRepositoryImpl(tonConnectDao, encryptedPreferences)
}
