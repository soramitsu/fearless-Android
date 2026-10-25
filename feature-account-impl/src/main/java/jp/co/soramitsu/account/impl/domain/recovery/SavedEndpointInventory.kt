package jp.co.soramitsu.account.impl.domain.recovery

import android.os.CancellationSignal
import javax.inject.Inject
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.recovery.EndpointInventoryReader
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointPage
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.EndpointField
import jp.co.soramitsu.coredb.recovery.EndpointValueChunk
import jp.co.soramitsu.coredb.recovery.EndpointValueChunkReader
import jp.co.soramitsu.coredb.recovery.LegacyEndpointInventoryReader
import jp.co.soramitsu.coredb.recovery.LegacyEndpointPage
import jp.co.soramitsu.coredb.recovery.LegacyEndpointField
import jp.co.soramitsu.coredb.recovery.LegacyEndpointValueChunk
import jp.co.soramitsu.coredb.recovery.LegacyEndpointValueChunkReader
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reads one bounded archive page for the recovery screen; failures stay on that screen. */
class SavedEndpointInventory @Inject constructor(private val database: AppDatabase) {
    /** Read one collection without merging historical evidence with current ownership. */
    suspend fun readInventoryPage(
        source: EndpointInventorySource,
        afterExclusiveRowId: Long? = null,
        beforeExclusiveRowId: Long? = null
    ): EndpointPage = withSavedEndpointReadDeadline { cancellation ->
        EndpointInventoryReader(database.openHelper.readableDatabase).readPage(
            cancellation, source, afterExclusiveRowId, beforeExclusiveRowId
        )
    }

    /** A live record part is bound to the exact revision selected from the page. */
    suspend fun readInventoryChunk(ref: EndpointRecordRef, field: EndpointField, byteOffset: Long): EndpointValueChunk =
        withSavedEndpointReadDeadline { cancellation ->
            EndpointValueChunkReader(database.openHelper.readableDatabase).readChunk(cancellation, ref, field, byteOffset)
        }

    suspend fun readPage(afterExclusiveRowId: Long? = null, beforeExclusiveRowId: Long? = null): LegacyEndpointPage =
        withSavedEndpointReadDeadline { cancellation ->
            LegacyEndpointInventoryReader(database.openHelper.readableDatabase).readPage(
                cancellation, afterExclusiveRowId = afterExclusiveRowId, beforeExclusiveRowId = beforeExclusiveRowId
            )
        }

    /** Reads only the requested bounded part; it never copies the whole saved value. */
    suspend fun readChunk(originalRowId: Long, field: LegacyEndpointField, byteOffset: Long): LegacyEndpointValueChunk =
        withSavedEndpointReadDeadline { cancellation ->
            LegacyEndpointValueChunkReader(database.openHelper.readableDatabase).readChunk(
                cancellation, originalRowId, field, byteOffset
            )
        }
}

/** Cancellation and the deadline reach SQLite even while the IO child is in a blocking query. */
internal suspend fun <T> withSavedEndpointReadDeadline(read: (CancellationSignal) -> T): T = coroutineScope {
    val signal = CancellationSignal()
    val deadline = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
        try { delay(3_000) } finally { signal.cancel() }
    }
    try { withContext(Dispatchers.IO) { read(signal) } } finally {
        deadline.cancel()
        signal.cancel()
    }
}
