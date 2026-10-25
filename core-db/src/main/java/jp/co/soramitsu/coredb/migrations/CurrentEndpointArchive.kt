package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

/** The immutable 81-to-82 input snapshot, independent of any earlier historical endpoint capture. */
internal object CurrentEndpointArchive {
    private val format = EndpointArchiveFormat.CURRENT

    val schemaObjects: Set<ReleasedRoomSchemaObjectSpec> get() = format.schemaObjects

    /** Capture the original SQLite values in the caller transaction; never overwrite an existing archive. */
    fun capture(
        database: SupportSQLiteDatabase,
        sourceEdgeVersion: Int,
        payloadLimits: TonUpgradePayloadLimits = TonUpgradePayloadLimits.PRODUCTION
    ): Boolean = format.capture(database, sourceEdgeVersion, payloadLimits)

    /** Absence is false; incomplete or unsupported snapshots throw without mutation. */
    fun requireSafe(database: SupportSQLiteDatabase): Boolean = format.requireSafe(database)

    internal fun requireSupportedSourceSchema(rows: List<ModernEndpointSchemaRow>): Boolean =
        format.requireSupportedSourceSchema(rows)
}
