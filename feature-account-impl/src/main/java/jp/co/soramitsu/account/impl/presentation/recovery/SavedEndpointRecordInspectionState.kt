package jp.co.soramitsu.account.impl.presentation.recovery

import jp.co.soramitsu.coredb.recovery.EndpointField
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.EndpointValueChunk

/** Holds only the selected collection, exact record revision and one bounded part under the PIN lease. */
data class SavedEndpointRecordInspectionState(
    val ref: EndpointRecordRef,
    val field: EndpointField,
    val byteOffset: Long = 0,
    val chunk: EndpointValueChunk? = null,
    val loading: Boolean = true,
    val readFailed: Boolean = false
)
