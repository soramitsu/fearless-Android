package jp.co.soramitsu.walletconnect.impl.di

import android.content.Context
import android.os.Build
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.model.WalletConnectMetadata
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.core.extrinsic.SigningRuntimeSource
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.walletconnect.impl.data.EncryptedWalletConnectStateStorage
import jp.co.soramitsu.walletconnect.impl.data.EncryptionUtilStateCipher
import jp.co.soramitsu.walletconnect.impl.data.LegacyWalletConnectSdkCleanup
import jp.co.soramitsu.walletconnect.impl.data.OkHttpVerifyKeySource
import jp.co.soramitsu.walletconnect.impl.presentation.ClientSessionClient
import jp.co.soramitsu.walletconnect.impl.presentation.PoolEvmTransactionSender
import jp.co.soramitsu.walletconnect.impl.presentation.WalletConnectInteractorImpl
import jp.co.soramitsu.walletconnect.impl.presentation.WalletConnectMethod
import jp.co.soramitsu.walletconnect.impl.presentation.chainRegistryRuntimeSource
import jp.co.soramitsu.walletconnect.impl.protocol.OkHttpRelayTransport
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectClient
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectClientConfig
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectStateStorage
import jp.co.soramitsu.walletconnect.impl.protocol.WcClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class WalletConnectModule {
    /** The one storage the client loads and a legacy import writes into before the client starts. */
    @Provides
    @Singleton
    fun provideWalletConnectStateStorage(context: Context, encryptionUtil: EncryptionUtil): WalletConnectStateStorage =
        EncryptedWalletConnectStateStorage(
            preferences = context.getSharedPreferences(EncryptedWalletConnectStateStorage.FILE_NAME, Context.MODE_PRIVATE),
            cipher = EncryptionUtilStateCipher(encryptionUtil)
        )

    @Provides
    @Singleton
    fun provideWalletConnectClient(
        context: Context,
        storage: WalletConnectStateStorage,
        okHttpClient: OkHttpClient
    ): WalletConnectClient = WalletConnectClient(
        storage = storage,
        transport = OkHttpRelayTransport(okHttpClient),
        verifyKeySource = OkHttpVerifyKeySource(okHttpClient),
        config = WalletConnectClientConfig(
            projectId = BuildConfig.WALLET_CONNECT_PROJECT_ID,
            packageName = context.packageName,
            metadata = FEARLESS_METADATA,
            supportedMethods = WalletConnectMethod.values().map { it.method }.toSet(),
            userAgent = "wc-2/fearless-kotlin-${BuildConfig.VERSION_NAME}/android-${Build.VERSION.RELEASE}"
        ),
        clock = WcClock.System,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    @Provides
    @Singleton
    fun provideLegacyWalletConnectSdkCleanup(context: Context): LegacyWalletConnectSdkCleanup {
        val markers = context.getSharedPreferences(EncryptedWalletConnectStateStorage.FILE_NAME, Context.MODE_PRIVATE)
        return LegacyWalletConnectSdkCleanup(context = context, markers = markers)
    }

    @Provides
    @Suppress("LongParameterList")
    fun provideWalletConnectInteractor(
        chainsRepository: ChainsRepository,
        ethereumSource: EthereumRemoteSource,
        accountRepository: AccountRepository,
        keypairProvider: KeypairProvider,
        chainRegistry: ChainRegistry,
        ethereumConnectionPool: EthereumConnectionPool,
        featureToggles: ProductFeatureToggleStore,
        walletConnectClient: WalletConnectClient,
        signingRuntimeSource: SigningRuntimeSource
    ): WalletConnectInteractor = WalletConnectInteractorImpl(
        chainsRepository = chainsRepository,
        ethereumSource = ethereumSource,
        accountRepository = accountRepository,
        keypairProvider = keypairProvider,
        runtimeSource = chainRegistryRuntimeSource(chainRegistry),
        signingRuntimeOf = { chain, version -> signingRuntimeSource.signingRuntime(chain, version) },
        transactionSender = PoolEvmTransactionSender(ethereumConnectionPool),
        featureToggles = featureToggles,
        sessionClient = ClientSessionClient(walletConnectClient, sessionScope)
    )

    private companion object {
        /** Delivers answers to dApps; shared by every interactor Hilt creates. */
        val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val FEARLESS_METADATA = WalletConnectMetadata(
            name = "Fearless wallet",
            description = "Defi wallet",
            url = "https://fearlesswallet.io",
            icons = listOf("https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/FW%20icon%20128.png"),
            redirect = "fearless-wallet-wc://request"
        )
    }
}
