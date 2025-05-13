package jp.co.soramitsu.wallet.impl.data.repository

import android.annotation.SuppressLint
import android.util.Log
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.core.models.Asset.PriceProvider
import jp.co.soramitsu.core.models.Asset.PriceProviderType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.TonSyncDataRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
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
    private val tonPricesService: TonPricesService,
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
                val tonPricesDeferred = async {
                    tonPricesService.load(fiatModel)
                }

                val allPrices = coingeckoPrices.await() + chainlinkPricesDeferred.await() + tonPricesDeferred.await()
                tokenPriceDao.insertTokensPrice(allPrices)
                coroutineContext.job.invokeOnCompletion {
                    val errorMessage = it?.let { "with error: ${it.message}" } ?: ""
                    Log.d("PricesSyncService", "prices sync completed $errorMessage")
                }

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

            TokenPriceLocal(priceId, currency.symbol, stat[currency.id], change)
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
                        currency.symbol,
                        price,
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

class TonPricesService(
    private val tonSyncDataRepository: TonSyncDataRepository,
    private val chainsRepository: ChainsRepository,
    private val accountRepository: AccountRepository
) {
    suspend fun load(fiatModel: FiatCurrency) = supervisorScope {
        val tonChains = chainsRepository.getChains().filter { it.ecosystem == Ecosystem.Ton }
        val accounts = accountRepository.allMetaAccounts()

        val accountIds = accounts.map { account ->
            tonChains.mapNotNull {
                account.tonPublicKey?.tonAccountId(it.isTestNet)
            }
        }.flatten()
        if (accountIds.isEmpty()) return@supervisorScope emptyList()

        supervisorScope {
            val accountsJettonsPricesDeferred = accountIds.map { accountId ->
                async {
                    val jettons = loadJettons(accountId, tonChains)
                    jettons.mapNotNull { jetton ->
                        val uppercasedId = fiatModel.id.uppercase()

                        val price = jetton.price?.prices?.getOrDefault(uppercasedId, null)
                        val diff24h = runCatching {
                            val diffStr = jetton.price?.diff24h?.getOrDefault(uppercasedId, null)
                            BigDecimal(diffStr?.replace("%", "")?.replace("+", "")?.replace("−", "-"))
                        }.getOrNull()

                        if (price != null && diff24h != null) {
                            TokenPriceLocal(
                                jetton.jetton.symbol,
                                fiatModel.symbol,
                                price,
                                diff24h
                            )
                        } else {
                            null
                        }
                    }
                }
            }

            val tonCoinPricesDeferred = async {
                val tonCoinPriceResponse = runCatching { tonSyncDataRepository.getTonCoinPrices() }.getOrNull() ?: return@async null
                val uppercasedId = fiatModel.id.uppercase()

                val price = tonCoinPriceResponse.prices[uppercasedId]?.toBigDecimal()
                val diff24h = runCatching {
                    val diffStr = tonCoinPriceResponse.diff24h[uppercasedId]
                    BigDecimal(diffStr?.replace("%", "")?.replace("+", "")?.replace("−", "-"))
                }.getOrNull()

                if (price != null && diff24h != null) {
                    TokenPriceLocal(
                        "the-open-network",
                        fiatModel.symbol,
                        price,
                        diff24h
                    )
                } else {
                    null
                }
            }

            val tonPrice = tonCoinPricesDeferred.await()?.let { listOf(it) } ?: emptyList()

            accountsJettonsPricesDeferred.awaitAll().flatten() + tonPrice
        }
    }

    private suspend fun loadJettons(accountId: String, chains: List<Chain>) = supervisorScope {
        val jettonsDeferred = chains.map { chain ->
            async {
                runCatching {
                    tonSyncDataRepository.getJettonBalances(
                        chain,
                        accountId
                    ).balances
                }.getOrNull() ?: emptyList()
            }
        }

        jettonsDeferred.awaitAll().flatten()
    }
}