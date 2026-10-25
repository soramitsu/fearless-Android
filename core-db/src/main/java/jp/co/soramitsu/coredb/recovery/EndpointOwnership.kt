package jp.co.soramitsu.coredb.recovery

/** Live ownership state. Attached is not active or verified; origin refers only to CURRENT. */
data class EndpointOwnership(val attached: Boolean, val deleted: Boolean, val originalSnapshotRowId: Long?) {
    init { require(!(attached && deleted)) }
}
