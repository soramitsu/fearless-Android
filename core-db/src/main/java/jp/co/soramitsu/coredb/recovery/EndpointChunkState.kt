package jp.co.soramitsu.coredb.recovery

/** Stale ownership never silently becomes a new revision or a payload-bearing response. */
enum class EndpointChunkState { AVAILABLE, NULL_VALUE, NON_BYTE_STORAGE, SOURCE_ABSENT, ROW_ABSENT, STALE_REVISION }
