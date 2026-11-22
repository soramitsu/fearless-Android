// Bech32m constants and the V1 contract-address layout.
// Infix bit operations keep explicit grouping.
@file:Suppress("MagicNumber", "UnnecessaryParentheses")

package jp.co.soramitsu.irohaconnect.impl.review

/** iroha-js `parseCanonicalContractAddress`: an exact lowercase V1 Bech32m `irohac1…` contract address. */
internal object IrohaContractAddress {
    private const val HRP = "irohac"
    private const val VERSION = 1
    private const val PAYLOAD_BYTES = 29
    private const val BECH32M_CONSTANT = 0x2bc830a3
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GENERATORS = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    @Suppress("ReturnCount") // One early return per check of iroha-js parseCanonicalContractAddress.
    fun isCanonical(value: String): Boolean {
        if (value.isEmpty() || value.length > 90 || value != value.lowercase()) return false
        val separator = value.lastIndexOf('1')
        if (separator <= 0 || separator > 83 || separator + 7 > value.length) return false
        val hrp = value.substring(0, separator)
        if (hrp.any { it.code !in 33..126 } || hrp != HRP) return false
        val values = value.substring(separator + 1).map { CHARSET.indexOf(it).takeIf { index -> index >= 0 } ?: return false }
        if (polymod(expandHrp(hrp) + values) != BECH32M_CONSTANT) return false
        val payload = fromFiveBits(values.dropLast(6)) ?: return false
        return payload.size == PAYLOAD_BYTES && payload[0] == VERSION
    }

    private fun expandHrp(hrp: String): List<Int> = hrp.map { it.code ushr 5 } + 0 + hrp.map { it.code and 0x1f }

    private fun polymod(values: List<Int>): Int {
        var checksum = 1
        values.forEach { value ->
            val top = checksum ushr 25
            checksum = ((checksum and 0x1ffffff) shl 5) xor value
            GENERATORS.forEachIndexed { index, generator -> if ((top ushr index) and 1 != 0) checksum = checksum xor generator }
        }
        return checksum
    }

    private fun fromFiveBits(values: List<Int>): List<Int>? {
        val output = mutableListOf<Int>()
        var accumulator = 0
        var bits = 0
        values.forEach { value ->
            accumulator = (accumulator shl 5) or value
            bits += 5
            while (bits >= 8) {
                bits -= 8
                output += (accumulator ushr bits) and 0xff
            }
            accumulator = if (bits == 0) 0 else accumulator and ((1 shl bits) - 1)
        }
        if (bits >= 5 || (bits > 0 && accumulator != 0)) return null
        return output
    }
}
