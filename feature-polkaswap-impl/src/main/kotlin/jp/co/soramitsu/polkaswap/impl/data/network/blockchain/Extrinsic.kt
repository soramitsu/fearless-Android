package jp.co.soramitsu.polkaswap.impl.data.network.blockchain

import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.polkaswap.api.models.WithDesired

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
