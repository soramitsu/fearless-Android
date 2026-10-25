package jp.co.soramitsu.coredb.recovery

/** One bounded read. Keysets are positions, not a whole-inventory snapshot or database lease. */
data class EndpointPage(
    val source: EndpointInventorySource,
    val present: Boolean,
    val provenance: EndpointSourceProvenance?,
    val records: List<EndpointRecord>,
    val previousBeforeRowId: Long?,
    val nextAfterRowId: Long?
) {
    init {
        require(records.size <= EndpointInventoryReader.MAXIMUM_PAGE_SIZE)
        require(records.all { it.ref.source == source })
        require(present || (records.isEmpty() && provenance == null && previousBeforeRowId == null && nextAfterRowId == null))
        require(present && source in setOf(EndpointInventorySource.HISTORICAL, EndpointInventorySource.UPGRADE_ENTRY,
            EndpointInventorySource.CURRENT) || provenance == null)
        require(!present || source !in setOf(EndpointInventorySource.HISTORICAL, EndpointInventorySource.UPGRADE_ENTRY,
            EndpointInventorySource.CURRENT) || provenance != null)
        require(records.isNotEmpty() || (previousBeforeRowId == null && nextAfterRowId == null))
    }
    override fun toString(): String = "EndpointPage(source=$source, present=$present, records=${records.size})"
}
