package jp.co.soramitsu.polkaswap.impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.network.runtime.model.QuoteResponse
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.models.names
import jp.co.soramitsu.polkaswap.api.models.toFilters
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetails
import jp.co.soramitsu.polkaswap.api.presentation.models.toModel
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chainWithAsset
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlin.math.max
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge

class PolkaswapInteractorImpl @Inject constructor(
    private val chainRegistry: ChainRegistry,
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val polkaswapRepository: PolkaswapRepository
) : PolkaswapInteractor {

    override val polkaswapChainId = soraMainChainId

    override suspend fun getAsset(assetId: String): Asset? {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val (chain, chainAsset) = chainRegistry.chainWithAsset(polkaswapChainId, assetId)

        return walletRepository.getAsset(metaAccount.id, metaAccount.accountId(chain)!!, chainAsset, chain.minSupportedVersion)!!
    }

    override suspend fun getAvailableDexes(): List<BigInteger> {
        return polkaswapRepository.getAvailableDexes(polkaswapChainId)
    }

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
        tokenFrom: Asset,
        tokenTo: Asset,
        amount: BigDecimal,
        desired: WithDesired,
        slippageTolerance: Float,
        market: Market
    ): SwapDetails? {
        val polkaswapUtilityAssetId = chainRegistry.getChain(polkaswapChainId).utilityAsset.id
        val feeToken = requireNotNull(getAsset(polkaswapUtilityAssetId))
        val curMarkets =
            if (market == Market.SMART) emptyList() else listOf(market)

        val amountInPlanks = feeToken.token.configuration.planksFromAmount(amount)

        val tokenFromId = requireNotNull(tokenFrom.token.configuration.currencyId)
        val tokenToId = requireNotNull(tokenTo.token.configuration.currencyId)

        val (bestDex, swapQuote) = getBestSwapQuote(
            tokenFromId = tokenFromId,
            tokenToId = tokenToId,
            amount = amountInPlanks,
            desired = desired,
            curMarkets = curMarkets
        ).let { it.first to it.second.toModel(feeToken.token.configuration) }

        if (swapQuote.amount == BigDecimal.ZERO) return null

        val minMax =
            (swapQuote.amount * BigDecimal.valueOf(slippageTolerance.toDouble() / 100)).let {
                if (desired == WithDesired.INPUT)
                    swapQuote.amount - it
                else
                    swapQuote.amount + it
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
            curMarkets.names(),
            desired
        )

        val networkFee = feeToken.token.configuration.amountFromPlanks(networkFeeInPlanks)
        val networkFeeModel = SwapDetails.NetworkFee(
            tokenName = feeToken.token.configuration.symbolToShow,
            tokenAmount = networkFee,
            fiatAmount = feeToken.token.fiatAmount(networkFee)?.formatAsCurrency(feeToken.token.fiatSymbol).orEmpty()
        )

        val per1 = amount.divide(swapQuote.amount, scale, RoundingMode.HALF_EVEN)
        val per2 = swapQuote.amount.divide(amount, scale, RoundingMode.HALF_EVEN)
        val per2Fiat = tokenTo.token.fiatAmount(per2)?.formatAsCurrency(tokenTo.token.fiatSymbol).orEmpty()
        val liquidityFee = swapQuote.fee

        val liquidityProviderFeeModel = SwapDetails.NetworkFee(
            tokenName = feeToken.token.configuration.symbolToShow,
            tokenAmount = liquidityFee,
            fiatAmount = feeToken.token.fiatAmount(liquidityFee)?.formatAsCurrency(feeToken.token.fiatSymbol).orEmpty()
        )

        return SwapDetails(
            fromTokenId = tokenFromId,
            toTokenId = tokenToId,
            fromTokenName = tokenFrom.token.configuration.symbolToShow.uppercase(),
            toTokenName = tokenTo.token.configuration.symbolToShow.uppercase(),
            fromTokenImage = tokenFrom.token.configuration.iconUrl,
            toTokenImage = tokenTo.token.configuration.iconUrl,
            fromTokenAmount = swapQuote.amount,
            toTokenAmount = per2,
            toTokenMinReceived = minMax,
            toFiatMinReceived = per2Fiat,
            networkFee = networkFeeModel,
            liquidityProviderFee = liquidityProviderFeeModel,
            fromTokenOnToToken = per1,
            toTokenOnFromToken = per2
        )
    }

    private suspend fun getBestSwapQuote(
        tokenFromId: String,
        tokenToId: String,
        amount: BigInteger,
        desired: WithDesired,
        curMarkets: List<Market>,
    ): Pair<Int, QuoteResponse> {
        return getAvailableDexes().map {
            val dexId = it.toInt()
            dexId to polkaswapRepository.getSwapQuote(
                chainId = polkaswapChainId,
                tokenFromId = tokenFromId,
                tokenToId = tokenToId,
                amount = amount,
                desired = desired,
                curMarkets = curMarkets,
                dexId = dexId
            )
        }.maxBy { it.second.amount }
    }
}
