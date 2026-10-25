package jp.co.soramitsu.common.data.storage

/** Visit bounded names without a count/aggregate-size gate or reading values. */
fun forEachMatchingPreferenceKey(
    keys: Iterable<String>,
    prefixes: Set<String>,
    maxKeyBytes: Int,
    action: (String) -> Unit
) {
    require(prefixes.isNotEmpty() && prefixes.size <= 32)
    require(prefixes.all { it.isNotEmpty() && it.length <= 256 && it.encodeToByteArray().size <= 256 })
    require(maxKeyBytes in 1..1_024)
    keys.forEach { key ->
        if (prefixes.none(key::startsWith)) return@forEach
        if (key.length > maxKeyBytes || key.encodeToByteArray().size > maxKeyBytes) return@forEach
        action(key)
    }
}
