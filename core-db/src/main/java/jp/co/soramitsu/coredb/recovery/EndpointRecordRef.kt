package jp.co.soramitsu.coredb.recovery

/** A reference for this database lifetime, not persistent authorization or mutation authority. */
data class EndpointRecordRef(val source: EndpointInventorySource, val rowId: Long, val revision: Long? = null) {
    init {
        require((source == EndpointInventorySource.OWNED) == (revision != null))
        require(revision == null || revision > 0)
    }
}
