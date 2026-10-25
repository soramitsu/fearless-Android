package jp.co.soramitsu.account.impl.presentation.recovery

import jp.co.soramitsu.coredb.recovery.LegacyEndpointField
import jp.co.soramitsu.coredb.recovery.LegacyEndpointValueChunk

/** Only the currently requested part is held; authentication revocation clears it. */
data class SavedEndpointInspectionState(
    val originalRowId: Long,
    val field: LegacyEndpointField,
    val byteOffset: Long = 0,
    val chunk: LegacyEndpointValueChunk? = null,
    val loading: Boolean = true,
    val readFailed: Boolean = false
)
