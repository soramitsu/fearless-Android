package jp.co.soramitsu.common.data.network.runtime.model

import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.fromUnsignedBytes
import jp.co.soramitsu.fearless_utils.extensions.requireHexPrefix

// todo there were a field which caused an errors:
// val weight: Long
// we weren't use it anywhere so I just removed it
// New response should have a struct like this:
// {
//      "weight":{"ref_time":164143000},
//      "class":"normal",
//      "partialFee":"15407544760"
// }

class FeeResponse(
    val inclusionFee: InclusionFee
)

class InclusionFee(
    private val baseFee: String?,
    private val lenFee: String?,
    private val adjustedWeightFee: String?,
) {
    val sum: BigInteger
        get() = baseFee.decodeBigInt() + lenFee.decodeBigInt() + adjustedWeightFee.decodeBigInt()

    private fun String?.decodeBigInt(): BigInteger {
        // because substrate returns hexes with different length:
        // 0x3b9aca00
        // 0x3486ced00
        // 0xb320334
        if (this == null) return BigInteger.ZERO
        return if (this.length.isEven.not()) {
            val withoutPrefix = removePrefix("0x")
            "0$withoutPrefix".requireHexPrefix()
        } else {
            this
        }.fromHex().fromUnsignedBytes()
    }

    private val Int.isEven: Boolean
        get() = this % 2 == 0
}
