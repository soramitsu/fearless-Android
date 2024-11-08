package jp.co.soramitsu.common.di.modules

import android.content.Context
import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.AndroidLogger
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.nomis.NomisApi
import jp.co.soramitsu.common.data.network.rpc.SocketSingleRequestExecutor
import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import jp.co.soramitsu.shared_utils.wsrpc.logging.Logger
import jp.co.soramitsu.shared_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.shared_utils.wsrpc.request.CoroutinesRequestExecutor
import jp.co.soramitsu.shared_utils.wsrpc.request.RequestExecutor
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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

    @Provides
    @Singleton
    fun provideTonApi(context: Context): TonApi {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .cache(Cache(File(context.cacheDir, HTTP_CACHE), CACHE_SIZE))
            .retryOnConnectionFailure(true)
            .addInterceptor {
                if(it.request().url.host.contains("keeper") || it.request().url.host.contains("testnet.tonapi.io")) {
                    val request = it.request().newBuilder().apply {
                        addHeader(
                            "Authorization",
                            "Bearer ${BuildConfig.FL_ANDROID_TON_API_KEY}"
                        )
                        addHeader("Accept", "application/json")
                    }.build()
                    it.proceed(request)
                } else {
                    it.proceed(it.request())
                }
            }
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))

        val gson = Gson()

        val retrofit = Retrofit.Builder()
            .client(builder.build())
            .baseUrl("https://keeper.tonapi.io/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(TonApi::class.java)
    }
}
