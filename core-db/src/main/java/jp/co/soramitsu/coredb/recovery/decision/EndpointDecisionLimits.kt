package jp.co.soramitsu.coredb.recovery.decision

/** Exact, replacement-free limits for a small explicit offline decision. */
internal object EndpointDecisionLimits {
    const val URL_BYTES = 8192
    const val LABEL_BYTES = 512
    const val TARGET_ID_BYTES = 512
    const val FINGERPRINT_BYTES = 8L * 1024 * 1024
    const val INTERNAL_CHUNK_BYTES = 64 * 1024

    fun requireText(value: String, maximum: Int, nonempty: Boolean = false) {
        require(!nonempty || value.isNotEmpty()) { "Endpoint decision text is empty" }
        var bytes = 0
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            bytes += when {
                char.code <= 0x7f -> 1
                char.code <= 0x7ff -> 2
                char.isHighSurrogate() -> {
                    require(index < value.length && value[index].isLowSurrogate()) { "Endpoint decision text is malformed" }
                    index++
                    4
                }
                char.isLowSurrogate() -> throw IllegalArgumentException("Endpoint decision text is malformed")
                else -> 3
            }
            require(bytes <= maximum) { "Endpoint decision text exceeds its limit" }
        }
    }

    fun requireDigest(value: String) {
        require(value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }) { "Endpoint decision digest is invalid" }
    }

    fun nextRevision(revision: Long): Long {
        check(revision in 1 until Long.MAX_VALUE) { "Endpoint decision revision is exhausted or invalid" }
        return revision + 1
    }
}
