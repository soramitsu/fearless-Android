package jp.co.soramitsu.common.di.modules

import android.content.Context
import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.AndroidLogger
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.rpc.SocketSingleRequestExecutor
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.impl.NetworkStateProvider
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

private const val HTTP_CACHE = "http_cache"
private const val CACHE_SIZE = 50L * 1024L * 1024L // 50 MiB
private const val TIMEOUT_SECONDS = 60L

@Module
class NetworkModule {

    @Provides
    @ApplicationScope
    fun provideAppLinksProvider(): AppLinksProvider {
        return AppLinksProvider(
            termsUrl = BuildConfig.TERMS_URL,
            privacyUrl = BuildConfig.PRIVACY_URL,
            payoutsLearnMore = BuildConfig.PAYOUTS_LEARN_MORE,
            twitterAccountTemplate = BuildConfig.TWITTER_ACCOUNT_TEMPLATE,
            setControllerLearnMore = BuildConfig.SET_CONTROLLER_LEARN_MORE
        )
    }

    @Provides
    @ApplicationScope
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
            builder.addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        }

        return builder.build()
    }

    @Provides
    @ApplicationScope
    fun provideLogger(): Logger = AndroidLogger()

    @Provides
    @ApplicationScope
    fun provideApiCreator(
        okHttpClient: OkHttpClient
    ): NetworkApiCreator {
        return NetworkApiCreator(okHttpClient, "https://placeholder.com")
    }

    @Provides
    @ApplicationScope
    fun httpExceptionHandler(
        resourceManager: ResourceManager
    ): HttpExceptionHandler = HttpExceptionHandler(resourceManager)

    @Provides
    @ApplicationScope
    fun provideSocketFactory() = WebSocketFactory()

    @Provides
    @ApplicationScope
    fun provideReconnector() = Reconnector()

    @Provides
    @ApplicationScope
    fun provideRequestExecutor() = RequestExecutor()

    @Provides
    fun provideSocketService(
        mapper: Gson,
        socketFactory: WebSocketFactory,
        logger: Logger,
        reconnector: Reconnector,
        requestExecutor: RequestExecutor
    ): SocketService = SocketService(mapper, logger, socketFactory, reconnector, requestExecutor)

    @Provides
    @ApplicationScope
    fun provideSocketSingleRequestExecutor(
        mapper: Gson,
        logger: Logger,
        socketFactory: WebSocketFactory,
        resourceManager: ResourceManager
    ) = SocketSingleRequestExecutor(mapper, logger, socketFactory, resourceManager)

    @Provides
    fun provideNetworkStateMixin(): NetworkStateMixin = NetworkStateProvider()

    @Provides
    @ApplicationScope
    fun provideJsonMapper() = Gson()
}
