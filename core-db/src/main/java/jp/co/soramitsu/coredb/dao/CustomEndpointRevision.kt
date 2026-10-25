package jp.co.soramitsu.coredb.dao

/** A live ownership mutation must never wrap or normalize corrupt revision metadata. */
internal fun nextCustomEndpointRevision(revision: Long): Long {
    check(revision > 0 && revision < Long.MAX_VALUE) { "Custom endpoint revision cannot advance" }
    return revision + 1
}
