package jp.co.soramitsu.coredb.recovery

/** Invalid/unknown ordinal or flag integers stay integers, with no coercion to a current binding. */
data class LegacyEndpointInteger(val storage: LegacyEndpointStorage, val byteLength: Long?, val value: Long?)
