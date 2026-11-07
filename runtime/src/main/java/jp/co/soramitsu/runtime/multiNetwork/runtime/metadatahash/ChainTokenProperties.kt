package jp.co.soramitsu.runtime.multiNetwork.runtime.metadatahash

import java.math.BigDecimal

/**
 * The primary token a chain states in `system_properties`: the first `tokenDecimals` and `tokenSymbol` entry, which is
 * what a runtime's `enable_metadata_hash(symbol, decimals)` build is configured with.
 */
class ChainTokenProperties(val decimals: Int, val symbol: String) {

    companion object {
        private const val MAX_DECIMALS = 255

        /** Reads the JSON object `system_properties` returns. Anything but one clear primary token is refused. */
        fun parse(properties: Map<*, *>): ChainTokenProperties {
            val decimals = first(properties["tokenDecimals"])
            val symbol = first(properties["tokenSymbol"])
            val decimalsValue = when (decimals) {
                is Number -> BigDecimal(decimals.toString()).let { value ->
                    runCatching { value.intValueExact() }.getOrNull()
                }
                else -> null
            }
            require(decimalsValue != null && decimalsValue in 0..MAX_DECIMALS) { "The chain states no token decimals" }
            require(symbol is String && symbol.isNotEmpty()) { "The chain states no token symbol" }
            return ChainTokenProperties(decimalsValue, symbol)
        }

        private fun first(value: Any?): Any? = if (value is List<*>) value.firstOrNull() else value
    }
}
