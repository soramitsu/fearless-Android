package jp.co.soramitsu.coredb.migrations

/**
 * Plaintext instrumentation fakes use the same fail-closed key-inventory
 * contract as production encrypted preferences. Keeping this bounded prevents
 * migration tests from silently bypassing orphan-secret reconciliation.
 */
internal fun boundedTestPreferenceKeys(
    keys: Iterable<String>,
    prefixes: Set<String>,
    maxResultCount: Int,
    maxKeyBytes: Int,
    maxTotalKeyBytes: Int,
    failOnOversizedMatch: Boolean
): Set<String> {
    require(prefixes.isNotEmpty())
    require(maxResultCount > 0)
    require(maxKeyBytes > 0)
    require(maxTotalKeyBytes >= maxKeyBytes)

    var totalKeyBytes = 0
    return buildSet {
        keys.sorted().forEach { key ->
            if (prefixes.none(key::startsWith)) return@forEach
            val keyBytes = key.toByteArray(Charsets.UTF_8).size
            if (keyBytes > maxKeyBytes) {
                check(!failOnOversizedMatch) {
                    "A test encrypted-preference key exceeds the safe bound"
                }
                return@forEach
            }
            check(size < maxResultCount) {
                "Test encrypted-preference keys exceed the result-count bound"
            }
            check(totalKeyBytes <= maxTotalKeyBytes - keyBytes) {
                "Test encrypted-preference keys exceed the total-byte bound"
            }
            add(key)
            totalKeyBytes += keyBytes
        }
    }
}
