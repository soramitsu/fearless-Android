package jp.co.soramitsu.wallet.impl.data.repository

import android.annotation.SuppressLint
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.core.models.Asset.PriceProvider
import jp.co.soramitsu.core.models.Asset.PriceProviderType
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class PricesSyncService(
    private val tokenPriceDao: TokenPriceDao,
    private val coingeckoPricesService: CoingeckoPricesService,
    private val chainlinkPricesService: ChainlinkPricesService,
    private val selectedFiat: SelectedFiat,
    private val availableFiatCurrencies: GetAvailableFiatCurrencies,
) {

    private val syncMutex = Mutex()
    private var pricesSyncJob: Job? = null

    @SuppressLint("LogNotTimber")
    suspend fun sync() = withContext(Dispatchers.Default) {
        syncMutex.withLock {
            if (pricesSyncJob?.isCompleted == false && pricesSyncJob?.isCancelled == false) return@withContext
            pricesSyncJob?.cancel()
            pricesSyncJob = coroutineScope {
                val selectedFiat = selectedFiat.get()
                val fiatModel = availableFiatCurrencies[selectedFiat]
                    ?: return@coroutineScope this.coroutineContext.job

                val coingeckoPrices = async { coingeckoPricesService.load(fiatModel) }

                val chainlinkPricesDeferred = async {
                    val price24hChange =
                        coingeckoPrices.await().associate { it.priceId to it.recentRateChange }

                            .filterValues { it != null }.cast<Map<String, BigDecimal>>()
                    chainlinkPricesService.load(fiatModel, price24hChange)
                }

                val allPrices = coingeckoPrices.await() + chainlinkPricesDeferred.await()
                tokenPriceDao.insertTokensPrice(allPrices)

                this.coroutineContext.job
            }
        }
    }
}

// priceId to [
//              currencyId to priceValue,
//              currencyId_24h_change to changeValue
//             ]
typealias CoingeckoResponse = Map<String, Map<String, BigDecimal>>

class CoingeckoPricesService(
    private val coingeckoApi: CoingeckoApi,
    private val chainsRepository: ChainsRepository
) {
    companion object {
        private const val COINGECKO_REQUEST_DELAY_MILLIS = 60 * 1000
    }
    private val cache = mutableMapOf<String, CoingeckoResponse>()
    private val timestamps = mutableMapOf<String, Long>()

    suspend fun load(currency: FiatCurrency): List<TokenPriceLocal> {
        val allAssets = chainsRepository.getChains().map { it.assets }.flatten()
        val priceIds = allAssets.mapNotNull { it.priceId }

        val cacheAlive = System.currentTimeMillis() < (timestamps.getOrDefault(currency.id, 0L) + COINGECKO_REQUEST_DELAY_MILLIS)
        val cacheExists = cache[currency.id] != null
        val shouldGetFromCache = cacheExists && cacheAlive

        val coingeckoPriceStats = if(shouldGetFromCache) {
            requireNotNull(cache[currency.id])
        } else {
            coingeckoApi.getAssetPrice(priceIds.joinToString(","), currency.id, true)
                .also {
                    timestamps[currency.id] = System.currentTimeMillis()
                    cache[currency.id] = it
                }
        }

        val tokenPrices = priceIds.mapNotNull { priceId ->
            val stat = coingeckoPriceStats[priceId] ?: return@mapNotNull null

            val changeKey = "${currency.id}_24h_change"
            val change = stat[changeKey]

            TokenPriceLocal(priceId, stat[currency.id], currency.symbol, change)
        }

        return tokenPrices
    }
}

class ChainlinkPricesService(
    private val ethereumSource: EthereumRemoteSource,
    private val chainsRepository: ChainsRepository
) {
    suspend fun load(
        currency: FiatCurrency,
        prices24hChange: Map<String, BigDecimal>
    ): List<TokenPriceLocal> {
        if (currency.id != "usd") {
            return emptyList()
        }
        val chains = chainsRepository.getChains()
        val chainlinkServiceProvider = chains.find { it.chainlinkProvider } ?: return emptyList()
        val chainlinkAssets = chains.map { it.assets }.flatten()
            .filter { it.priceProvider?.type == PriceProviderType.Chainlink }

        return supervisorScope {
            chainlinkAssets.map { asset ->
                async {
                    val priceProvider = asset.priceProvider ?: return@async null
                    val price =
                        getChainlinkPrices(
                            priceProvider = priceProvider,
                            chainId = chainlinkServiceProvider.id
                        )
                            ?: return@async null

                    TokenPriceLocal(
                        priceProvider.id,
                        price,
                        currency.symbol,
                        prices24hChange.getOrDefault(asset.priceId, null)
                    )
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun getChainlinkPrices(
        priceProvider: PriceProvider,
        chainId: ChainId
    ): BigDecimal? {
        return runCatching {
            ethereumSource.fetchPriceFeed(
                chainId = chainId,
                receiverAddress = priceProvider.id
            )?.let { price ->
                BigDecimal(price, priceProvider.precision)
            }
        }.getOrNull()
    }
}