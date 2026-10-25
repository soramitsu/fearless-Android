package jp.co.soramitsu.coredb.recovery

/** Only AVAILABLE contains text; toString deliberately excludes endpoint data. */
data class LegacyEndpointText(
    val storage: LegacyEndpointStorage,
    val byteLength: Long?,
    val state: LegacyEndpointTextState,
    val value: String?
) {
    override fun toString(): String = "LegacyEndpointText(storage=$storage,bytes=$byteLength,state=$state)"
}
