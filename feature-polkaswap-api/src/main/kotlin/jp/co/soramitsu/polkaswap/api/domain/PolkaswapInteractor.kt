package jp.co.soramitsu.polkaswap.api.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.polkaswap.api.domain.models.SwapDetails
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PolkaswapInteractor {

    val polkaswapChainId: String

    suspend fun getAsset(assetId: String): Asset?
    suspend fun getAvailableDexes(): List<BigInteger>
    fun observePoolReserves(fromTokenId: String, toTokenId: String, market: Market): Flow<String>

    suspend fun calcDetails(
        availableDexPaths: List<Int>,
        tokenFrom: Asset,
        tokenTo: Asset,
        amount: BigDecimal,
        desired: WithDesired,
        slippageTolerance: Double,
        market: Market
    ): Result<SwapDetails?>

    suspend fun fetchAvailableSources(tokenInput: Asset, tokenOutput: Asset, availableDexes: List<Int>): Set<Market>
    val bestDexIdFlow: StateFlow<LoadingState<Int>>
    val availableMarkets: MutableMap<Int, List<Market>>

    suspend fun swap(
        dexId: Int,
        inputAssetId: String,
        outputAssetId: String,
        amount: BigInteger,
        limit: BigInteger,
        filter: String,
        markets: List<String>,
        desired: WithDesired
    ): Result<String>

    fun setChainId(chainId: ChainId?)
    suspend fun getFeeAsset(): Asset?
    suspend fun calcFakeFee(): BigDecimal
    suspend fun estimateSwapFee(
        bestDex: Int,
        tokenFromId: String,
        tokenToId: String,
        amountInPlanks: BigInteger,
        market: Market,
        desired: WithDesired
    ): BigInteger

    suspend fun getAvailableDexesForPair(tokenFromId: String, tokenToId: String, dexes: List<BigInteger>): List<Int>
}
