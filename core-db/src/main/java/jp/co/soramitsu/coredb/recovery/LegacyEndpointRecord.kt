package jp.co.soramitsu.coredb.recovery

data class LegacyEndpointRecord(
    val originalRowId: Long,
    val name: LegacyEndpointText,
    val endpoint: LegacyEndpointText,
    val networkType: LegacyEndpointInteger,
    val isDefault: LegacyEndpointInteger,
    val isActive: LegacyEndpointInteger
) {
    override fun toString(): String = "LegacyEndpointRecord(originalRowId=$originalRowId)"
}
