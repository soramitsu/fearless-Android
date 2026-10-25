package jp.co.soramitsu.coredb.model.chain

/** Shared bound for one operational endpoint row; raw archives and ownership are independently bounded. */
internal object EndpointProjectionLimits {
    const val MAX_ROW_BYTES = 1_048_576L
    const val SCALAR_ALLOWANCE_BYTES = 128L
    const val MAX_TEXT_BYTES = MAX_ROW_BYTES - SCALAR_ALLOWANCE_BYTES

    /** Count exact UTF-8 bytes without a payload copy or replacement encoding of malformed UTF-16. */
    fun requireTextFits(chainId: String, url: String, name: String) {
        var bytes = 0L
        for (value in listOf(chainId, url, name)) {
            var index = 0
            while (index < value.length) {
                val char = value[index++]
                bytes += when {
                    char.code <= 0x7f -> 1
                    char.code <= 0x7ff -> 2
                    char.isHighSurrogate() -> {
                        require(index < value.length && value[index].isLowSurrogate()) {
                            "Endpoint projection text contains an unpaired surrogate"
                        }
                        index++
                        4
                    }
                    char.isLowSurrogate() -> throw IllegalArgumentException("Endpoint projection text contains an unpaired surrogate")
                    else -> 3
                }
                require(bytes <= MAX_TEXT_BYTES) { "Endpoint projection text exceeds the safe limit" }
            }
        }
    }
}
