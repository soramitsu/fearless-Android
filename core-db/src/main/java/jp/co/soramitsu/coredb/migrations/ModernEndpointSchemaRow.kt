package jp.co.soramitsu.coredb.migrations

/** Bounded schema metadata only. Endpoint values are never represented by this model. */
internal data class ModernEndpointSchemaRow(
    val rowid: Long,
    val type: String,
    val name: String,
    val owner: String,
    val sql: String?
)
