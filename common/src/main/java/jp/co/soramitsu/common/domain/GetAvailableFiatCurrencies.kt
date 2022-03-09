package jp.co.soramitsu.common.domain

import java.util.Calendar
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class GetAvailableFiatCurrencies(private val coingeckoApi: CoingeckoApi) {

    private var cache = listOf<FiatCurrency>()
    private var syncTimeMillis = 0L
    private val minRatesRefreshDuration = 2.toDuration(DurationUnit.HOURS)

    operator fun get(index: String): FiatCurrency? = cache.firstOrNull { it.id == index }

    suspend operator fun invoke(): List<FiatCurrency> {
        val shouldRefreshRates = Calendar.getInstance().timeInMillis - syncTimeMillis > minRatesRefreshDuration.toInt(DurationUnit.MILLISECONDS)
        if (shouldRefreshRates) {
            val supportedCurrencies = coingeckoApi.getSupportedCurrencies()
            val config = coingeckoApi.getFiatConfig()
            cache = config.filter { it.id in supportedCurrencies }
            syncTimeMillis = Calendar.getInstance().timeInMillis
        }

        return cache
    }
}
