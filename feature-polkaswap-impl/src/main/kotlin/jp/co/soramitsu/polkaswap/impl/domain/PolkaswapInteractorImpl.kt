package jp.co.soramitsu.polkaswap.impl.domain

import android.util.Log
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.core.runtime.models.responses.QuoteResponse
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.domain.InsufficientLiquidityException
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.domain.models.SwapDetails
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.models.backStrings
import jp.co.soramitsu.polkaswap.api.models.toFilters
import jp.co.soramitsu.polkaswap.api.presentation.models.toModel
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

class PolkaswapInteractorImpl @Inject constructor(
    private val chainRegistry: ChainRegistry,
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val polkaswapRepository: PolkaswapRepository,
    private val sharedPreferences: Preferences,
    private val chainsRepository: ChainsRepository,
    private val coroutineContext: CoroutineContext = Dispatchers.Default + CoroutineExceptionHandler { _, throwable -> Log.e("PolkaswapInteractor", "$throwable ${throwable.message}") }
) : PolkaswapInteractor {

    override var polkaswapChainId = soraMainChainId
    override val availableMarkets: MutableMap<Int, List<Market>> = mutableMapOf(0 to listOf(Market.SMART))
    override val bestDexIdFlow: MutableStateFlow<LoadingState<Int>> = MutableStateFlow(LoadingState.Loaded(0))
    override var hasReadDisclaimer: Boolean
        get() = sharedPreferences.getBoolean(PolkaswapInteractor.HAS_READ_DISCLAIMER_KEY, false)
        set(value) {
            sharedPreferences.putBoolean(PolkaswapInteractor.HAS_READ_DISCLAIMER_KEY, value)
        }

    override fun observeHasReadDisclaimer(): Flow<Boolean> {
        return sharedPreferences.booleanFlow(PolkaswapInteractor.HAS_READ_DISCLAIMER_KEY, false)
    }

    override fun setChainId(chainId: ChainId?) {
        chainId?.takeIf { it in listOf(soraMainChainId, soraTestChainId) }?.let { polkaswapChainId = chainId }
    }

    override suspend fun getFeeAsset(): Asset? = withContext(coroutineContext) {
        val chain = chainsRepository.getChain(polkaswapChainId)
        chain.utilityAsset?.id?.let { getAsset(it) }
    }

    override suspend fun getAsset(assetId: String): Asset? = withContext(coroutineContext) {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val (chain, chainAsset) = chainsRepository.chainWithAsset(polkaswapChainId, assetId)

        walletRepository.getAsset(metaAccount.id, metaAccount.accountId(chain)!!, chainAsset, chain.minSupportedVersion)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun assetFlow(chainAssetId: String): Flow<Asset> {
        return accountRepository.selectedMetaAccountFlow().flatMapLatest { metaAccount ->

            val (chain, chainAsset) = chainsRepository.chainWithAsset(polkaswapChainId, chainAssetId)
            val accountId = metaAccount.accountId(chain)!!

            walletRepository.assetFlow(
                metaAccount.id,
                accountId,
                chainAsset,
                chain.minSupportedVersion
            )
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun getAvailableDexes(): List<BigInteger> = withContext(coroutineContext) {
        polkaswapRepository.getAvailableDexes(polkaswapChainId)
    }

    @OptIn(FlowPreview::class)
    override fun observePoolReserves(fromTokenId: String, toTokenId: String, market: Market): Flow<String> {
        val flows = mutableListOf<Flow<String>>()
        if (market == Market.XYK || market == Market.SMART) {
            flows.add(
                polkaswapRepository.observePoolXYKReserves(
                    polkaswapChainId,
                    fromTokenId,
                    toTokenId
                )
            )
        }
        if (market == Market.TBC || market == Market.SMART) {
            flows.add(polkaswapRepository.observePoolTBCReserves(polkaswapChainId, fromTokenId))
            flows.add(polkaswapRepository.observePoolTBCReserves(polkaswapChainId, toTokenId))
        }
        return flows.merge().debounce(500).flowOn(Dispatchers.Default)
    }

    override suspend fun calcDetails(
        availableDexPaths: List<Int>,
        tokenFrom: Asset,
        tokenTo: Asset,
        amount: BigDecimal,
        desired: WithDesired,
        slippageTolerance: Double,
        market: Market
    ): Result<SwapDetails?> = withContext(coroutineContext) {
        val polkaswapUtilityAssetId = chainsRepository.getChain(polkaswapChainId).utilityAsset?.id
        val feeAsset = requireNotNull(polkaswapUtilityAssetId?.let { getAsset(it) })

        val curMarkets = if (market == Market.SMART) emptyList() else listOf(market)

        val amountInPlanks = feeAsset.token.configuration.planksFromAmount(amount)

        val tokenFromId = requireNotNull(tokenFrom.token.configuration.currencyId)
        val tokenToId = requireNotNull(tokenTo.token.configuration.currencyId)

        val previousBestDex = bestDexIdFlow.value
        if (market !in availableMarkets.values.flatten().toSet()) return@withContext Result.success(null)
        bestDexIdFlow.emit(LoadingState.Loading())
        val (bestDex, swapQuote) = getBestSwapQuote(
            dexes = availableDexPaths,
            tokenFromId = tokenFromId,
            tokenToId = tokenToId,
            amount = amountInPlanks,
            desired = desired,
            curMarkets = curMarkets
        )?.let { it.first to it.second.toModel(feeAsset.token.configuration) } ?: run {
            bestDexIdFlow.emit(previousBestDex)
            return@withContext Result.failure(InsufficientLiquidityException())
        }
        bestDexIdFlow.emit(LoadingState.Loaded(bestDex))

        if (swapQuote.amount.isZero()) return@withContext Result.success(null)

        val minMax =
            (swapQuote.amount * BigDecimal.valueOf(slippageTolerance / 100)).let {
                if (desired == WithDesired.INPUT) {
                    swapQuote.amount - it
                } else {
                    swapQuote.amount + it
                }
            }

        val scale = max(swapQuote.amount.scale(), amount.scale())

        if (swapQuote.amount.isZero()) return@withContext Result.success(null)
        val per1 = amount.divide(swapQuote.amount, scale, RoundingMode.HALF_EVEN)
        val per2 = swapQuote.amount.divide(amount, scale, RoundingMode.HALF_EVEN)

        val (fromTokenOnToToken, toTokenOnFromToken) = when (desired) {
            WithDesired.INPUT -> per1 to per2
            WithDesired.OUTPUT -> per2 to per1
        }

        val polkaswapAssets = chainsRepository.getChain(polkaswapChainId).assets
        val route = swapQuote.route?.joinToString("  ➝  ") { routeId ->
            polkaswapAssets.firstOrNull { it.currencyId == routeId }?.symbol ?: "?"
        }?.uppercase()

        val details = SwapDetails(
            amount = swapQuote.amount,
            minMax = minMax,
            fromTokenOnToToken = fromTokenOnToToken,
            toTokenOnFromToken = toTokenOnFromToken,
            feeAsset = feeAsset,
            bestDexId = bestDex,
            route = route
        )
        return@withContext Result.success(details)
    }

    private suspend fun getBestSwapQuote(
        dexes: List<Int>,
        tokenFromId: String,
        tokenToId: String,
        amount: BigInteger,
        desired: WithDesired,
        curMarkets: List<Market>
    ): Pair<Int, QuoteResponse>? = withContext(coroutineContext) {
        val quotes = dexes.mapNotNull { dexId ->
            val quote = polkaswapRepository.getSwapQuote(
                chainId = polkaswapChainId,
                tokenFromId = tokenFromId,
                tokenToId = tokenToId,
                amount = amount,
                desired = desired,
                curMarkets = curMarkets,
                dexId = dexId
            ) ?: return@mapNotNull null

            dexId to quote
        }
        return@withContext if (quotes.isEmpty()) {
            null
        } else {
            when (desired) {
                WithDesired.INPUT -> quotes.maxBy { it.second.amount }
                WithDesired.OUTPUT -> quotes.minBy { it.second.amount }
            }
        }
    }

    override suspend fun estimateSwapFee(
        bestDex: Int,
        tokenFromId: String,
        tokenToId: String,
        amountInPlanks: BigInteger,
        market: Market,
        desired: WithDesired
    ): BigInteger = withContext(coroutineContext) {
        val curMarkets = if (market == Market.SMART) emptyList() else listOf(market)
        return@withContext polkaswapRepository.estimateSwapFee(
            polkaswapChainId,
            bestDex,
            tokenFromId,
            tokenToId,
            amountInPlanks,
            amountInPlanks,
            curMarkets.toFilters(),
            curMarkets.backStrings(),
            desired
        )
    }

    override suspend fun fetchAvailableSources(tokenInput: Asset, tokenOutput: Asset, availableDexes: List<Int>): Set<Market> = withContext(coroutineContext) {
        val tokenFromId = requireNotNull(tokenInput.token.configuration.currencyId)
        val tokenToId = requireNotNull(tokenOutput.token.configuration.currencyId)

        val sources = polkaswapRepository.getAvailableSources(polkaswapChainId, tokenFromId, tokenToId, availableDexes)
            .mapValues { listOf(Market.SMART, *it.value.toTypedArray()) }

        availableMarkets.clear()
        availableMarkets.putAll(sources)
        return@withContext sources.values.flatten().toSet()
    }

    override suspend fun getAvailableDexesForPair(tokenFromId: String, tokenToId: String, dexes: List<BigInteger>): List<Int> = withContext(coroutineContext) {
        return@withContext dexes.map {
            val isAvailable = polkaswapRepository.isPairAvailable(polkaswapChainId, tokenFromId, tokenToId, it.toInt())

            it.toInt() to isAvailable
        }.filter { it.second }.map { it.first }
    }

    override suspend fun swap(
        dexId: Int,
        inputAssetId: String,
        outputAssetId: String,
        amount: BigInteger,
        limit: BigInteger,
        filter: String,
        markets: List<String>,
        desired: WithDesired
    ): Result<String> = withContext(coroutineContext) {
        polkaswapRepository.swap(polkaswapChainId, dexId, inputAssetId, outputAssetId, amount, limit, filter, markets, desired)
    }

    override suspend fun calcFakeFee(): BigDecimal = withContext(coroutineContext) {
        val feeAsset = getFeeAsset() ?: return@withContext BigDecimal.ZERO
        val feeAssetId = feeAsset.token.configuration.currencyId ?: return@withContext BigDecimal.ZERO
        val markets = emptyList<Market>()

        val fee = polkaswapRepository.estimateSwapFee(
            polkaswapChainId,
            0,
            feeAssetId,
            feeAssetId,
            BigInteger.ONE,
            BigInteger.ONE,
            markets.toFilters(),
            markets.backStrings(),
            WithDesired.INPUT
        )
        return@withContext feeAsset.token.configuration.amountFromPlanks(fee)
    }
}
