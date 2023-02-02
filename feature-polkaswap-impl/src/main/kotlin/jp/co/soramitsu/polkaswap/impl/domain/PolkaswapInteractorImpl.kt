package jp.co.soramitsu.polkaswap.impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.network.runtime.model.QuoteResponse
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.domain.InsufficientLiquidityException
import jp.co.soramitsu.polkaswap.api.domain.PathUnavailableException
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.domain.models.SwapDetails
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.models.backStrings
import jp.co.soramitsu.polkaswap.api.models.toFilters
import jp.co.soramitsu.polkaswap.api.presentation.models.toModel
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.runtime.multiNetwork.chainWithAsset
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlin.math.max
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge

class PolkaswapInteractorImpl @Inject constructor(
    private val chainRegistry: ChainRegistry,
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val polkaswapRepository: PolkaswapRepository
) : PolkaswapInteractor {

    override var polkaswapChainId = soraMainChainId
    override val availableMarkets: MutableMap<Int, List<Market>> = mutableMapOf(0 to listOf(Market.SMART))
    override val bestDexIdFlow: MutableStateFlow<LoadingState<Int>> = MutableStateFlow(LoadingState.Loaded(0))

    override fun setChainId(chainId: ChainId?) {
        chainId?.takeIf { it in listOf(soraMainChainId, soraTestChainId) }?.let { polkaswapChainId = chainId }
    }

    override suspend fun getFeeAsset(): Asset? {
        val chain = chainRegistry.getChain(polkaswapChainId)
        return getAsset(chain.utilityAsset.id)
    }

    override suspend fun getAsset(assetId: String): Asset? {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val (chain, chainAsset) = chainRegistry.chainWithAsset(polkaswapChainId, assetId)

        return walletRepository.getAsset(metaAccount.id, metaAccount.accountId(chain)!!, chainAsset, chain.minSupportedVersion)!!
    }

    override suspend fun getAvailableDexes(): List<BigInteger> {
        return polkaswapRepository.getAvailableDexes(polkaswapChainId)
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
        return flows.merge().debounce(500)
    }

    override suspend fun calcDetails(
        dexes: List<BigInteger>,
        tokenFrom: Asset,
        tokenTo: Asset,
        amount: BigDecimal,
        desired: WithDesired,
        slippageTolerance: Double,
        market: Market
    ): Result<SwapDetails?> {
        val polkaswapUtilityAssetId = chainRegistry.getChain(polkaswapChainId).utilityAsset.id
        val feeAsset = requireNotNull(getAsset(polkaswapUtilityAssetId))
        val curMarkets =
            if (market == Market.SMART) emptyList() else listOf(market)

        val amountInPlanks = feeAsset.token.configuration.planksFromAmount(amount)

        val tokenFromId = requireNotNull(tokenFrom.token.configuration.currencyId)
        val tokenToId = requireNotNull(tokenTo.token.configuration.currencyId)

        val availableDexPaths = getAvailableDexesForPair(tokenFromId, tokenToId, dexes)

        if (availableDexPaths.isEmpty()) {
            return Result.failure(PathUnavailableException())
        }
        val previousBestDex = bestDexIdFlow.value
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
            return Result.failure(InsufficientLiquidityException())
        }

        bestDexIdFlow.emit(LoadingState.Loaded(bestDex))

        if (swapQuote.amount.compareTo(BigDecimal.ZERO) == 0) return Result.success(null)

        val minMax =
            (swapQuote.amount * BigDecimal.valueOf(slippageTolerance / 100)).let {
                if (desired == WithDesired.INPUT) {
                    swapQuote.amount - it
                } else {
                    swapQuote.amount + it
                }
            }

        val scale = max(swapQuote.amount.scale(), amount.scale())

        val networkFeeInPlanks = polkaswapRepository.estimateSwapFee(
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

        val networkFee = feeAsset.token.configuration.amountFromPlanks(networkFeeInPlanks)

        if (swapQuote.amount.compareTo(BigDecimal.ZERO) == 0) return Result.success(null)
        val per1 = amount.divide(swapQuote.amount, scale, RoundingMode.HALF_EVEN)
        val per2 = swapQuote.amount.divide(amount, scale, RoundingMode.HALF_EVEN)
        val liquidityFee = swapQuote.fee

        val (fromTokenOnToToken, toTokenOnFromToken) = when (desired) {
            WithDesired.INPUT -> per1 to per2
            WithDesired.OUTPUT -> per2 to per1
        }

        val details = SwapDetails(
            amount = swapQuote.amount,
            minMax = minMax,
            networkFee = networkFee,
            liquidityProviderFee = liquidityFee,
            fromTokenOnToToken = fromTokenOnToToken,
            toTokenOnFromToken = toTokenOnFromToken,
            feeAsset = feeAsset
        )
        return Result.success(details)
    }

    private suspend fun getBestSwapQuote(
        dexes: List<Int>,
        tokenFromId: String,
        tokenToId: String,
        amount: BigInteger,
        desired: WithDesired,
        curMarkets: List<Market>
    ): Pair<Int, QuoteResponse>? {
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
        return if (quotes.isEmpty()) {
            null
        } else {
            quotes.maxBy { it.second.amount }
        }
    }

    override suspend fun fetchAvailableSources(tokenInput: Asset, tokenOutput: Asset, dexes: List<BigInteger>) {
        val tokenFromId = requireNotNull(tokenInput.token.configuration.currencyId)
        val tokenToId = requireNotNull(tokenOutput.token.configuration.currencyId)

        val availableDexPaths = getAvailableDexesForPair(tokenFromId, tokenToId, dexes)

        if (availableDexPaths.isEmpty()) {
            return
        }

        val sources = polkaswapRepository.getAvailableSources(polkaswapChainId, tokenFromId, tokenToId, availableDexPaths)
            .mapValues { listOf(Market.SMART, *it.value.toTypedArray()) }
        availableMarkets.clear()
        availableMarkets.putAll(sources)
    }

    private suspend fun getAvailableDexesForPair(tokenFromId: String, tokenToId: String, dexes: List<BigInteger>): List<Int> {
        return dexes.map {
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
    ): Result<String> {
        return polkaswapRepository.swap(polkaswapChainId, dexId, inputAssetId, outputAssetId, amount, limit, filter, markets, desired)
    }

    override suspend fun calcFakeFee(): BigDecimal {
        val feeAsset = getFeeAsset() ?: return BigDecimal.ZERO
        val feeAssetId = feeAsset.token.configuration.currencyId ?: return BigDecimal.ZERO
        val markets = emptyList<Market>()
        val liquidityProxyFee = polkaswapRepository.getSwapQuote(
            chainId = polkaswapChainId,
            tokenFromId = feeAssetId,
            tokenToId = "0x0200080000000000000000000000000000000000000000000000000000000000",
            amount = BigInteger.ONE,
            desired = WithDesired.INPUT,
            curMarkets = markets,
            dexId = 0
        )?.fee.orZero()

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
        return feeAsset.token.configuration.amountFromPlanks(fee) + feeAsset.token.configuration.amountFromPlanks(liquidityProxyFee)
    }
}
