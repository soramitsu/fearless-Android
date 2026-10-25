package jp.co.soramitsu.coredb.recovery

data class LegacyEndpointPage(
    val archivePresent: Boolean,
    val records: List<LegacyEndpointRecord>,
    /** Adjacent cursors use bounded lookahead/probes; no total count is computed. */
    val previousBeforeRowId: Long?,
    val nextAfterRowId: Long?
)
