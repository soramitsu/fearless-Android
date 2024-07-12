package jp.co.soramitsu.common.data.network.config

import jp.co.soramitsu.common.BuildConfig
import retrofit2.http.GET
import retrofit2.http.Header

interface RemoteConfigFetcher {
    @GET(BuildConfig.APP_CONFIG_URL)
    suspend fun getAppConfig(
        @Header("Cache-Control") noCache: String = "no-cache"
    ): AppConfigRemote

    @GET(BuildConfig.POLKASWAP_CONFIG_URL)
    suspend fun getPolkaswapConfig(): PolkaswapRemoteConfig

    @GET(BuildConfig.FEATURE_TOGGLE_URL)
    suspend fun getFeatureToggle(): FeatureToggleConfig

    @GET("https://api.nomis.cc/api/v1/multichain-score/wallet/0xd8da6bf26964af9d7eed9e03e53415d37aa96045/score")
    suspend fun getNomisVitalikScore(
        @Header("X-API-Key") apiKey: String = "j9Us1Kxoo9fs3nD",
        @Header("X-ClientId") clientId: String = "FCEB90FC-E3F9-4CF5-980E-A8111A3FFF31"
    ): String
}
