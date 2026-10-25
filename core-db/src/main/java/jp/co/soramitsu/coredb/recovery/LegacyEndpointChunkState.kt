package jp.co.soramitsu.coredb.recovery

/** Absence and unsupported SQLite storage never masquerade as an empty byte value. */
enum class LegacyEndpointChunkState {
    AVAILABLE,
    ARCHIVE_ABSENT,
    ROW_ABSENT,
    NULL_VALUE,
    UNSUPPORTED_STORAGE
}
