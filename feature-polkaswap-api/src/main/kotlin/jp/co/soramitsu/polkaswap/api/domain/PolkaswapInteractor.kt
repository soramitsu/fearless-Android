package jp.co.soramitsu.polkaswap.api.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.polkaswap.api.domain.models.SwapDetails
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PolkaswapInteractor {

    val polkaswapChainId: String

    suspend fun getAsset(assetId: String): Asset?
    suspend fun getAvailableDexes(): List<BigInteger>
    fun observePoolReserves(fromTokenId: String, toTokenId: String, market: Market): Flow<String>

    suspend fun calcDetails(
        dexes: List<BigInteger>,
        tokenFrom: Asset,
        tokenTo: Asset,
        amount: BigDecimal,
        desired: WithDesired,
        slippageTolerance: Double,
        market: Market
    ): Result<SwapDetails?>

    suspend fun fetchAvailableSources(tokenInput: Asset, tokenOutput: Asset, dexes: List<BigInteger>)
    val bestDexIdFlow: StateFlow<LoadingState<Int>>
    val availableMarkets: MutableMap<Int, List<Market>>
}
