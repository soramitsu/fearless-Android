package jp.co.soramitsu.coredb.recovery.decision

/** Exact attached owner identity displayed by the current authenticated page; never resolved by URL alone. */
data class EndpointSelectionOwner(val id: Long, val revision: Long, val originalSnapshotRowId: Long?) {
    init { require(revision > 0) }
}
