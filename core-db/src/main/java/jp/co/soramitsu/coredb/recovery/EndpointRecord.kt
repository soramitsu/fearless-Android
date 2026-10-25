package jp.co.soramitsu.coredb.recovery

/** Source-specific raw values and live ownership metadata are never conflated. */
data class EndpointRecord(
    val ref: EndpointRecordRef,
    val values: Map<EndpointField, EndpointValue>,
    val ownership: EndpointOwnership? = null
) {
    init {
        require(values.keys == endpointSourceSpec(ref.source).fields.keys)
        require((ref.source == EndpointInventorySource.OWNED) == (ownership != null))
    }
    override fun toString(): String = "EndpointRecord(ref=$ref, fields=${values.keys}, ownership=$ownership)"
}
