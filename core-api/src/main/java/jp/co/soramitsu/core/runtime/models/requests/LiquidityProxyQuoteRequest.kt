package jp.co.soramitsu.core.runtime.models.requests

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import java.math.BigInteger

@Suppress("LongParameterList")
class LiquidityProxyQuoteRequest(
    dexId: Int,
    inputAssetId: String,
    outputAssetId: String,
    amount: BigInteger,
    swapVariant: String,
    selectedSourceTypes: List<String>,
    filterMode: String
) : RuntimeRequest(
    method = "liquidityProxy_quote",
    params = listOf(
        dexId,
        inputAssetId,
        outputAssetId,
        amount.toString(),
        swapVariant,
        selectedSourceTypes,
        filterMode
    )
)
