package jp.co.soramitsu.polkaswap.impl.data.network.blockchain

import java.math.BigDecimal
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.extrinsic.ExtrinsicBuilder
import java.math.BigInteger
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount

fun ExtrinsicBuilder.swap(
    dexId: Int,
    inputAssetId: String,
    outputAssetId: String,
    amount: BigInteger,
    limit: BigInteger,
    filter: String,
    markets: List<String>,
    desired: WithDesired
) =
    this.call(
        "LiquidityProxy",
        "swap",
        mapOf(
            "dex_id" to dexId.toBigInteger(),
            "input_asset_id" to Struct.Instance(
                mapOf("code" to inputAssetId.fromHex().toList().map { it.toInt().toBigInteger() })
            ),
            "output_asset_id" to Struct.Instance(
                mapOf("code" to outputAssetId.fromHex().toList().map { it.toInt().toBigInteger() })
            ),
            "swap_amount" to DictEnum.Entry(
                name = desired.backString,
                value = Struct.Instance(
                    (if (desired == WithDesired.INPUT) "desired_amount_in" to "min_amount_out" else "desired_amount_out" to "max_amount_in").let {
                        mapOf(
                            it.first to amount,
                            it.second to limit
                        )
                    }
                )
            ),
            "selected_source_types" to markets.map { DictEnum.Entry(it, null) },
            "filter_mode" to DictEnum.Entry(
                name = filter,
                value = null
            )
        )
    )

fun ExtrinsicBuilder.register(
    dexId: Int,
    baseAssetId: String,
    targetAssetId: String
) = this.call(
    "TradingPair",
    "register",
    mapOf(
        "dex_id" to dexId.toBigInteger(),
        "base_asset_id" to baseAssetId.mapCodeToken(),
        "target_asset_id" to targetAssetId.mapCodeToken(),
    )
)

fun ExtrinsicBuilder.initializePool(
    dexId: Int,
    baseAssetId: String,
    targetAssetId: String
) = this.call(
    Modules.POOL_XYK,
    "initialize_pool",
    mapOf(
        "dex_id" to dexId.toBigInteger(),
        "asset_a" to baseAssetId.mapCodeToken(),
        "asset_b" to targetAssetId.mapCodeToken(),
    )
)

fun ExtrinsicBuilder.depositLiquidity(
    dexId: Int,
    baseAssetId: String,
    targetAssetId: String,
    baseAssetAmount: BigInteger,
    targetAssetAmount: BigInteger,
    amountFromMin: BigInteger,
    amountToMin: BigInteger
) = this.call(
    Modules.POOL_XYK,
    "deposit_liquidity",
    mapOf(
        "dex_id" to dexId.toBigInteger(),
        "input_asset_a" to baseAssetId.mapCodeToken(),
        "input_asset_b" to targetAssetId.mapCodeToken(),
        "input_a_desired" to baseAssetAmount,
        "input_b_desired" to targetAssetAmount,
        "input_a_min" to amountFromMin,
        "input_b_min" to amountToMin
    )
)

fun ExtrinsicBuilder.removeLiquidity(
    dexId: Int,
    outputAssetIdA: String,
    outputAssetIdB: String,
    markerAssetDesired: BigInteger,
    outputAMin: BigInteger,
    outputBMin: BigInteger
) =
    this.call(
        Modules.POOL_XYK,
        "withdraw_liquidity",
        mapOf(
            "dex_id" to dexId.toBigInteger(),
            "output_asset_a" to outputAssetIdA.mapCodeToken(),
            "output_asset_b" to outputAssetIdB.mapCodeToken(),
            "marker_asset_desired" to markerAssetDesired,
            "output_a_min" to outputAMin,
            "output_b_min" to outputBMin
        )
    )

fun ExtrinsicBuilder.liquidityAdd(
    dexId: Int,
    baseTokenId: String?,
    targetTokenId: String?,
    pairPresented: Boolean,
    pairEnabled: Boolean,
    tokenBaseAmount: BigInteger,
    tokenTargetAmount: BigInteger,
    amountBaseMin: BigInteger,
    amountTargetMin: BigInteger
) {
    if (baseTokenId != null && targetTokenId != null) {
        if (!pairPresented) {
            if (!pairEnabled) {
                register(
                    dexId = dexId,
                    baseTokenId,
                    targetTokenId
                )
            }
            initializePool(
                dexId = dexId,
                baseTokenId,
                targetTokenId
            )
        }

        depositLiquidity(
            dexId = dexId,
            baseTokenId,
            targetTokenId,
            tokenBaseAmount,
            tokenTargetAmount,
            amountBaseMin,
            amountTargetMin,
        )
    }
}

fun String.mapCodeToken() = Struct.Instance(
    mapOf("code" to this.mapAssetId())
)

fun String.mapAssetId() = this.fromHex().mapAssetId()
fun ByteArray.mapAssetId() = this.toList().map { it.toInt().toBigInteger() }
