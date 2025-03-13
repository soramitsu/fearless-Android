package jp.co.soramitsu.common.domain

import android.util.Log
import java.util.Calendar
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

typealias FiatCurrencies = List<FiatCurrency>

class GetAvailableFiatCurrencies(private val coingeckoApi: CoingeckoApi) {

    private var cache = MutableStateFlow<FiatCurrencies>(listOf())
    private var syncTimeMillis = 0L
    private val minRatesRefreshDuration = 2.toDuration(DurationUnit.HOURS)

    operator fun get(index: String): FiatCurrency? = cache.value.firstOrNull { it.id == index }

    suspend operator fun invoke(): FiatCurrencies = withContext(Dispatchers.IO) {
        sync()
        return@withContext cache.value
    }

    fun flow(): Flow<FiatCurrencies> = cache

    suspend fun sync() {
        val shouldRefreshRates = Calendar.getInstance().timeInMillis - syncTimeMillis > minRatesRefreshDuration.toInt(DurationUnit.MILLISECONDS)
        if (shouldRefreshRates) {
            runCatching {
                val supportedCurrencies = coingeckoApi.getSupportedCurrencies()
                val config = coingeckoApi.getFiatConfig()
                cache.value = config.filter { it.id in supportedCurrencies }
                syncTimeMillis = Calendar.getInstance().timeInMillis
            }.onFailure { Log.d("GetAvailableFiatCurrencies", "GetAvailableFiatCurrencies sync failed: $it") }
        }
    }
}

operator fun FiatCurrencies.get(index: String) = firstOrNull { it.id == index }
