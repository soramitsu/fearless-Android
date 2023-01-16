package jp.co.soramitsu.polkaswap.impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetails
import jp.co.soramitsu.polkaswap.api.presentation.models.toModel
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chainWithAsset
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
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

    override suspend fun getAvailableDexes(chainId: ChainId): List<BigInteger> {
        return polkaswapRepository.getAvailableDexes(chainId)
    }

    override fun observePoolReserves(chainId: ChainId, fromTokenId: String, toTokenId: String, market: Market): Flow<String> {
        val flows = mutableListOf<Flow<String>>()
        if (market == Market.XYK || market == Market.SMART) {
            flows.add(
                polkaswapRepository.observePoolXYKReserves(
                    chainId,
                    fromTokenId,
                    toTokenId
                )
            )
        }
        if (market == Market.TBC || market == Market.SMART) {
            flows.add(polkaswapRepository.observePoolTBCReserves(chainId, fromTokenId))
            flows.add(polkaswapRepository.observePoolTBCReserves(chainId, toTokenId))
        }
        return flows.merge().debounce(500)
    }

    override suspend fun calcDetails(
        chainId: ChainId,
        tokenFromId: String,
        tokenToId: String,
        feeToken: Chain.Asset,
        amount: BigDecimal,
        desired: WithDesired,
        slippageTolerance: Float,
        market: Market
    ): SwapDetails? {
        val curMarkets =
            if (market == Market.SMART) emptyList() else listOf(market)

        val swapQuote = polkaswapRepository.getSwapQuote(
            chainId = chainId,
            tokenFromId = tokenFromId,
            tokenToId = tokenToId,
            amount = feeToken.planksFromAmount(amount),
            desired = desired,
            curMarkets = curMarkets,
            dexId = 0 // todo
        ).toModel(feeToken)

        if (swapQuote.amount == BigDecimal.ZERO) return null

        val minMax =
            (swapQuote.amount * BigDecimal.valueOf(slippageTolerance.toDouble() / 100)).let {
                if (desired == WithDesired.INPUT)
                    swapQuote.amount - it
                else
                    swapQuote.amount + it
            }

        val scale = max(swapQuote.amount.scale(), amount.scale())
        val networkFee =
            swapNetworkFee ?: (fetchSwapNetworkFee(feeToken).also { swapNetworkFee = it })
        poolReservesFlowToken.value = tokenFrom.id to tokenTo.id
        return SwapDetails(
            swapQuote.amount,
            amount.divide(swapQuote.amount, scale, RoundingMode.HALF_EVEN),
            swapQuote.amount.divide(amount, scale, RoundingMode.HALF_EVEN),
            minMax,
            swapQuote.fee,
            networkFee,
        )
    }

}
