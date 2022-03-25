package jp.co.soramitsu.common.data.network.config

import jp.co.soramitsu.common.BuildConfig
import retrofit2.http.GET
import retrofit2.http.Header

interface RemoteConfigFetcher {
    @GET(BuildConfig.APP_CONFIG_URL)
    suspend fun getAppConfig(
        @Header("Cache-Control") noCache: String = "no-cache"
    ): AppConfigRemote
}
