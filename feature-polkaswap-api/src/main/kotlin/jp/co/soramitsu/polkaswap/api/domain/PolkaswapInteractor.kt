package jp.co.soramitsu.polkaswap.api.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.polkaswap.api.domain.models.OkxCrossChainSwapDetails
import jp.co.soramitsu.polkaswap.api.domain.models.OkxSwapDetails
import jp.co.soramitsu.polkaswap.api.domain.models.PolkaswapSwapDetails
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import jp.co.soramitsu.core.models.Asset as ChainAsset

interface PolkaswapInteractor {
    companion object {
        const val HAS_READ_DISCLAIMER_KEY = "hasReadDisclaimer"
    }

    val polkaswapChainId: String
    var hasReadDisclaimer: Boolean

    suspend fun getAsset(assetId: String): Asset?
    fun assetFlow(chainAssetId: String): Flow<Asset>
    suspend fun getAvailableDexes(): List<BigInteger>
    fun observePoolReserves(fromTokenId: String, toTokenId: String, market: Market): Flow<String>

    suspend fun calcDetails(
        availableDexPaths: List<Int>,
        fromAsset: ChainAsset,
        toAsset: ChainAsset,
        amount: BigDecimal,
        desired: WithDesired,
        slippageTolerance: Double,
        market: Market
    ): Result<PolkaswapSwapDetails?>

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
    fun observeHasReadDisclaimer(): Flow<Boolean>

    suspend fun crossChainBuildTx(
        fromAsset: ChainAsset,
        toAsset: ChainAsset,
        amount: String,
        sort: Int? = null, // 0 - default
        slippage: String,  // 0.002 - 0.5
        userWalletAddress: String,
    ): Result<OkxCrossChainSwapDetails?>

    suspend fun getOkxSwap(
        fromAsset: ChainAsset,
        toAsset: ChainAsset,
        amount: String,
        slippage: String,
        userWalletAddress: String,
    ): Result<OkxSwapDetails?>
}
