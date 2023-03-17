package jp.co.soramitsu.common.data.network.coingecko

import jp.co.soramitsu.common.BuildConfig
import retrofit2.http.GET
import retrofit2.http.Query
import java.math.BigDecimal

interface CoingeckoApi {

    @GET("//api.coingecko.com/api/v3/simple/price")
    suspend fun getAssetPrice(
        @Query("ids") priceIds: String,
        @Query("vs_currencies") currency: String,
        @Query("include_24hr_change") includeRateChange: Boolean
    ): Map<String, Map<String, BigDecimal>>

    @GET("//api.coingecko.com/api/v3/simple/price")
    suspend fun getSingleAssetPrice(
        @Query("ids") priceIds: String,
        @Query("vs_currencies") currency: String
    ): Map<String, Map<String, Double>>

    @GET("//api.coingecko.com/api/v3/simple/supported_vs_currencies")
    suspend fun getSupportedCurrencies(): List<String>

    @GET(BuildConfig.FIAT_CONFIG_URL)
    suspend fun getFiatConfig(): List<FiatCurrency>
}
