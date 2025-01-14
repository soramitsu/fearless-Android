package jp.co.soramitsu.common.di.modules

import android.content.Context
import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.AndroidLogger
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.OptionsProvider
import jp.co.soramitsu.common.data.network.nomis.NomisApi
import jp.co.soramitsu.common.data.network.rpc.SocketSingleRequestExecutor
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import jp.co.soramitsu.shared_utils.wsrpc.logging.Logger
import jp.co.soramitsu.shared_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.shared_utils.wsrpc.request.CoroutinesRequestExecutor
import jp.co.soramitsu.shared_utils.wsrpc.request.RequestExecutor
import jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.api.BlockExplorerRepository
import jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.impl.BlockExplorerRepositoryImpl
import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.api.ConfigDAO
import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.api.data.ConfigParser
import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.impl.SuperWalletConfigDAOImpl
import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.impl.data.RemoteConfigParserImpl
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.HistoryItemsFilter
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.TxHistoryRepository
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxHistoryItem
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.impl.TxHistoryRepositoryImpl
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.impl.builder.ExpectActualDBDriverFactory
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.AbstractRestClientConfig
import jp.co.soramitsu.xnetworking.lib.engines.rest.impl.RestClientImpl
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

private const val HTTP_CACHE = "http_cache"
private const val NOMIS_CACHE = "nomis_cache"
private const val CACHE_SIZE = 50L * 1024L * 1024L // 50 MiB
private const val TIMEOUT_SECONDS = 60L
private const val NOMIS_TIMEOUT_MINUTES = 2L

@InstallIn(SingletonComponent::class)
@Module
class NetworkModule {

    @Singleton
    @Provides
    fun provideRestClient(
        json: Json,
    ): RestClient = RestClientImpl(
        restClientConfig = object : AbstractRestClientConfig() {
            override fun getConnectTimeoutMillis(): Long = 30_000L
            override fun getOrCreateJsonConfig(): Json = json
            override fun getRequestTimeoutMillis(): Long = 30_000L
            override fun getSocketTimeoutMillis(): Long = 30_000L
            override fun isLoggingEnabled(): Boolean = BuildConfig.DEBUG
        }
    )

    @Singleton
    @Provides
    fun provideConfigDAO(configParser: ConfigParser): ConfigDAO =
        SuperWalletConfigDAOImpl(configParser = configParser)

    @Singleton
    @Provides
    fun provideTxHistoryRepository(
        @ApplicationContext cnt: Context,
        configDAO: ConfigDAO,
        restClient: RestClient,
    ): TxHistoryRepository {
        return TxHistoryRepositoryImpl(
            historyItemsFilter = object : HistoryItemsFilter {
                override fun List<TxHistoryItem>.filterCachedHistoryItems(): List<TxHistoryItem> = this

                override fun List<TxHistoryItem>.filterPagedHistoryItems(): List<TxHistoryItem> = this
            },
            restClient = restClient,
            configDAO = configDAO,
            databaseDriverFactory = ExpectActualDBDriverFactory(
                context = cnt,
                name = "fearlessxndb",
            )
        )
    }

    @Singleton
    @Provides
    fun provideSoraWalletBlockExplorerInfo(
        configDAO: ConfigDAO,
        restClient: RestClient,
        repo: TxHistoryRepository,
    ): BlockExplorerRepository {
        return BlockExplorerRepositoryImpl(
            configDAO = configDAO,
            restClient = restClient,
            txHistoryRepository = repo,
        )
    }

    @Singleton
    @Provides
    fun provideConfigParser(
        restClient: RestClient
    ): ConfigParser = RemoteConfigParserImpl(
        restClient = restClient,
        chainsRequestUrl = "https://raw.githubusercontent.com/soramitsu/shared-features-utils/MWR-819/chains/xn.json",
    )

    @Provides
    @Singleton
    fun provideAppLinksProvider(): AppLinksProvider {
        return AppLinksProvider(
            termsUrl = BuildConfig.TERMS_URL,
            privacyUrl = BuildConfig.PRIVACY_URL,
            payoutsLearnMore = BuildConfig.PAYOUTS_LEARN_MORE,
            twitterAccountTemplate = BuildConfig.TWITTER_ACCOUNT_TEMPLATE,
            setControllerLearnMore = BuildConfig.SET_CONTROLLER_LEARN_MORE,
            moonbeamStakingLearnMore = BuildConfig.MOONBEAM_STAKING_LEARN_MORE
        )
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        context: Context
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .cache(Cache(File(context.cacheDir, HTTP_CACHE), CACHE_SIZE))
            .retryOnConnectionFailure(true)

        if (BuildConfig.DEBUG) {
//            builder.addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideNomisHttpClient(context: Context): NomisApi {
        val builder = OkHttpClient.Builder()
            .connectTimeout(NOMIS_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .writeTimeout(NOMIS_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .readTimeout(NOMIS_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .callTimeout(NOMIS_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .cache(Cache(File(context.cacheDir, NOMIS_CACHE), CACHE_SIZE))
            .addInterceptor {
                val request = it.request().newBuilder().apply {
                    addHeader("X-API-Key", "j9Us1Kxoo9fs3nD")
                    addHeader("X-ClientId", "FCEB90FC-E3F9-4CF5-980E-A8111A3FFF31")
                }.build()
                it.proceed(request)
            }
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .cacheControl(
                        CacheControl.Builder()
                        .maxAge(24, TimeUnit.HOURS)
                        .build())
                    .build()
                chain.proceed(request)
            }
            .retryOnConnectionFailure(true)

        val gson = Gson()

        val retrofit = Retrofit.Builder()
            .client(builder.build())
            .baseUrl("https://api.nomis.cc/api/v1/multichain-score/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(NomisApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLogger(): Logger = AndroidLogger()

    @Provides
    @Singleton
    fun provideApiCreator(
        okHttpClient: OkHttpClient
    ): NetworkApiCreator {
        return NetworkApiCreator(okHttpClient, "https://placeholder.com")
    }

    @Provides
    @Singleton
    fun httpExceptionHandler(
        resourceManager: ResourceManager
    ): HttpExceptionHandler = HttpExceptionHandler(resourceManager)

    @Provides
    @Singleton
    fun provideSocketFactory() = WebSocketFactory()

    @Provides
    @Singleton
    fun provideReconnector() = Reconnector()

    @Provides
    @Singleton
    fun provideRequestExecutor(): RequestExecutor = CoroutinesRequestExecutor()

    @Provides
    fun provideSocketService(
        mapper: Gson,
        socketFactory: WebSocketFactory,
        logger: Logger,
        reconnector: Reconnector,
        requestExecutor: RequestExecutor
    ): SocketService = SocketService(mapper, logger, socketFactory, reconnector, requestExecutor)

    @Provides
    @Singleton
    fun provideSocketSingleRequestExecutor(
        mapper: Gson,
        logger: Logger,
        socketFactory: WebSocketFactory,
        resourceManager: ResourceManager
    ) = SocketSingleRequestExecutor(mapper, logger, socketFactory, resourceManager)

    @Provides
    @Singleton
    fun provideJsonMapper() = Gson()
}
