package jp.co.soramitsu.account.impl.domain.recovery

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.recovery.LegacyEndpointField
import jp.co.soramitsu.coredb.recovery.LegacyEndpointChunkState
import jp.co.soramitsu.coredb.recovery.LegacyEndpointPage
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointPage
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.EndpointField
import jp.co.soramitsu.coredb.recovery.EndpointChunkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.mockito.MockedConstruction
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Draft JVM bridge coverage. Android SQLite interruption is qualified separately by the reader's native cases. */
class SavedEndpointInventoryTest {
    @Test
    fun actualInventoryReadsAbsentArchiveOnIoAndForwardsOwnedSignalToEveryMetadataQuery() {
        val fixture = DatabaseFixture()
        val caller = Thread.currentThread()
        withSignals { construction ->
            val result = runBlocking { SavedEndpointInventory(fixture.database).readPage() }
            assertEquals(LegacyEndpointPage(false, emptyList(), null, null), result)
            val signal = construction.constructed().single()
            assertEquals(3, fixture.queries.size)
            assertEquals("PRAGMA encoding", fixture.queries.first())
            assertTrue(fixture.signals.all { it === signal })
            assertNotSame(caller, fixture.queryThread.get())
            verify(signal, atLeastOnce()).cancel()
            fixture.assertReadOnlyAndClosed()
        }
    }

    @Test
    fun actualInventoryKeepsMutuallyExclusiveCursorValidationAndCancelsOnFailure() {
        val fixture = DatabaseFixture()
        withSignals { construction ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { SavedEndpointInventory(fixture.database).readPage(afterExclusiveRowId = -1, beforeExclusiveRowId = 2) }
            }
            assertTrue(fixture.queries.isEmpty())
            verify(construction.constructed().single(), atLeastOnce()).cancel()
            fixture.assertReadOnlyAndClosed()
        }
    }

    @Test
    fun actualInventoryPropagatesMetadataFailureWithoutFabricatedEmptyPage() {
        val failure = FixtureReadFailure("Synthetic metadata IO failure")
        val fixture = DatabaseFixture(failure)
        withSignals { construction ->
            assertSame(failure, assertThrows(FixtureReadFailure::class.java) {
                runBlocking { SavedEndpointInventory(fixture.database).readPage(afterExclusiveRowId = Long.MIN_VALUE) }
            })
            verify(construction.constructed().single(), atLeastOnce()).cancel()
            fixture.assertReadOnlyAndClosed()
        }
    }

    @Test
    fun actualInventoryDatabaseAcquisitionFailureStaysAnErrorAndReleasesSignal() {
        val database: AppDatabase = mock()
        val failure = FixtureReadFailure("Synthetic database acquisition failure")
        whenever(database.openHelper).thenThrow(failure)
        withSignals { construction ->
            assertSame(failure, assertThrows(FixtureReadFailure::class.java) {
                runBlocking { SavedEndpointInventory(database).readPage() }
            })
            verify(construction.constructed().single(), atLeastOnce()).cancel()
        }
    }

    @Test
    fun bridgeReturnsSuccessfulReadAndCancelsPendingDeadlineWithoutOwningDatabase() {
        val callbackSignal = AtomicReference<CancellationSignal>()
        val callbackThread = AtomicReference<Thread>()
        val caller = Thread.currentThread()
        withSignals { construction ->
            val result = runBlocking {
                withSavedEndpointReadDeadline { signal ->
                    callbackSignal.set(signal)
                    callbackThread.set(Thread.currentThread())
                    "bounded page"
                }
            }
            assertEquals("bounded page", result)
            assertSame(construction.constructed().single(), callbackSignal.get())
            assertNotSame(caller, callbackThread.get())
            verify(callbackSignal.get(), atLeastOnce()).cancel()
        }
    }

    @Test
    fun bridgePreservesReadExceptionAndCancelsPendingDeadline() {
        val failure = FixtureReadFailure("Synthetic bounded query failure")
        withSignals { construction ->
            assertSame(failure, assertThrows(FixtureReadFailure::class.java) {
                runBlocking { withSavedEndpointReadDeadline<Nothing> { throw failure } }
            })
            verify(construction.constructed().single(), atLeastOnce()).cancel()
        }
    }

    @Test(timeout = 12_000)
    fun parentCancellationReachesHeldBlockingReadBeforeItsDeadlineAndDoesNotDeliverLateResult() {
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val readReturned = AtomicBoolean(false)
        val delivered = AtomicBoolean(false)
        withSignals(onCancel = { cancelled.countDown() }) { construction ->
            runBlocking {
                // UNDISPATCHED constructs the Android signal on this test thread,
                // where Mockito's construction scope is active. The read still uses actual IO.
                val request = async(start = CoroutineStart.UNDISPATCHED) {
                    withSavedEndpointReadDeadline {
                        entered.countDown()
                        check(cancelled.await(8, TimeUnit.SECONDS)) { "Bridge never cancelled its blocked read" }
                        readReturned.set(true)
                        "obsolete page"
                    }.also { delivered.set(true) }
                }
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                assertEquals("The deadline must not already have released this probe", 1L, cancelled.count)
                request.cancel()
                assertTrue("Parent cancellation must reach the signal before the default3s deadline", cancelled.await(1, TimeUnit.SECONDS))
                withTimeout(5_000) { request.join() }
                assertTrue(request.isCancelled)
                assertTrue(readReturned.get())
                assertFalse(delivered.get())
                try { request.await(); fail("Cancelled request returned a page") } catch (_: CancellationException) { }
            }
            verify(construction.constructed().single(), atLeastOnce()).cancel()
        }
    }

    @Test(timeout = 12_000)
    fun actualThreeSecondDeadlineCancelsHeldReadWithoutParentCancellation() {
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val failure = FixtureReadFailure("Synthetic SQLite cancellation delivery")
        withSignals(onCancel = { cancelled.countDown() }) { construction ->
            assertSame(failure, assertThrows(FixtureReadFailure::class.java) {
                runBlocking {
                    withSavedEndpointReadDeadline<Nothing> {
                        entered.countDown()
                        check(cancelled.await(8, TimeUnit.SECONDS)) { "Deadline never reached its blocking read" }
                        throw failure
                    }
                }
            })
            assertEquals(0L, entered.count)
            assertEquals(0L, cancelled.count)
            verify(construction.constructed().single(), atLeastOnce()).cancel()
        }
    }

    @Test
    fun actualChunkInventoryReadsAbsentArchiveOnIoAndForwardsOwnedSignalToEveryMetadataQuery() {
        val fixture = DatabaseFixture()
        val caller = Thread.currentThread()
        withSignals { construction ->
            val result = runBlocking {
                SavedEndpointInventory(fixture.database).readChunk(Long.MIN_VALUE, LegacyEndpointField.NAME, 512)
            }
            assertEquals(LegacyEndpointChunkState.ARCHIVE_ABSENT, result.state)
            assertEquals(Long.MIN_VALUE, result.originalRowId)
            assertEquals(LegacyEndpointField.NAME, result.field)
            assertEquals(512L, result.byteOffset)
            assertNull(result.bytes)
            val signal = construction.constructed().single()
            assertEquals(3, fixture.queries.size)
            assertEquals("PRAGMA encoding", fixture.queries.first())
            assertTrue(fixture.signals.all { it === signal })
            assertNotSame(caller, fixture.queryThread.get())
            verify(signal, atLeastOnce()).cancel()
            fixture.assertReadOnlyAndClosed()
        }
    }

    @Test
    fun everyGlobalCollectionUsesTheIoDeadlineSignalAndRetainsAbsenceWithoutWrites() {
        EndpointInventorySource.values().forEach { source ->
            val fixture = DatabaseFixture()
            val caller = Thread.currentThread()
            withSignals { construction ->
                val result = runBlocking { SavedEndpointInventory(fixture.database).readInventoryPage(source) }
                assertEquals(EndpointPage(source, false, null, emptyList(), null, null), result)
                val signal = construction.constructed().single()
                assertTrue(fixture.queries.isNotEmpty())
                assertTrue(fixture.signals.all { it === signal })
                assertNotSame(caller, fixture.queryThread.get())
                verify(signal, atLeastOnce()).cancel()
                fixture.assertReadOnlyAndClosed()
            }
        }
    }

    @Test
    fun everyGlobalChunkKeepsSourceRevisionAndOffsetWhileForwardingCancellation() {
        EndpointInventorySource.values().forEach { source ->
            val fixture = DatabaseFixture()
            val caller = Thread.currentThread()
            val ref = EndpointRecordRef(source, Long.MIN_VALUE, 7L.takeIf { source == EndpointInventorySource.OWNED })
            withSignals { construction ->
                val result = runBlocking {
                    SavedEndpointInventory(fixture.database).readInventoryChunk(ref, EndpointField.NAME, 512)
                }
                assertEquals(EndpointChunkState.SOURCE_ABSENT, result.state)
                assertEquals(ref, result.ref)
                assertEquals(EndpointField.NAME, result.field)
                assertEquals(512L, result.byteOffset)
                assertNull(result.bytes)
                val signal = construction.constructed().single()
                assertTrue(fixture.signals.all { it === signal })
                assertNotSame(caller, fixture.queryThread.get())
                verify(signal, atLeastOnce()).cancel()
                fixture.assertReadOnlyAndClosed()
            }
        }
    }

    // An identity field prevents coroutine debug stack recovery from cloning this
    // controlled failure, so identity assertions examine the original error.
    private class FixtureReadFailure(message: String) : RuntimeException(message) {
        @Suppress("unused") val marker = Any()
    }

    private fun <T> withSignals(
        onCancel: (CancellationSignal) -> Unit = {},
        action: (MockedConstruction<CancellationSignal>) -> T
    ): T = mockConstruction(CancellationSignal::class.java) { signal, _ ->
        doAnswer { onCancel(signal); null }.whenever(signal).cancel()
    }.use(action)

    private class DatabaseFixture(private val metadataFailure: RuntimeException? = null) {
        val queries = ConcurrentLinkedQueue<String>()
        val signals = ConcurrentLinkedQueue<CancellationSignal>()
        val cursors = ConcurrentLinkedQueue<Cursor>()
        val queryThread = AtomicReference<Thread>()
        val sqlite: SupportSQLiteDatabase = mock {
            on { query(any<SupportSQLiteQuery>(), any<CancellationSignal>()) } doAnswer { invocation ->
                queryThread.set(Thread.currentThread())
                val query = invocation.arguments[0] as SupportSQLiteQuery
                queries += query.sql
                signals += invocation.arguments[1] as CancellationSignal
                when {
                    query.sql == "PRAGMA encoding" -> cursor(listOf("UTF-8"))
                    query.sql.contains("sqlite_temp_master") || query.sql.contains("FROM temp.sqlite_master") -> cursor(emptyList())
                    query.sql.contains("FROM main.sqlite_master") -> {
                        metadataFailure?.let { throw it }
                        cursor(emptyList())
                    }
                    else -> error("Absent archive must not trigger payload query: ${query.sql}")
                }
            }
        }
        private val helper: SupportSQLiteOpenHelper = mock { on { readableDatabase } doAnswer { sqlite } }
        val database: AppDatabase = mock { on { openHelper } doAnswer { helper } }

        fun assertReadOnlyAndClosed() {
            verify(sqlite, never()).query(any<String>())
            verify(sqlite, never()).query(any<String>(), any<Array<Any?>>())
            verify(sqlite, never()).query(any<SupportSQLiteQuery>())
            verify(sqlite, never()).execSQL(any<String>())
            verify(sqlite, never()).beginTransaction()
            verify(sqlite, never()).close()
            verify(database, never()).close()
            cursors.forEach { verify(it).close() }
        }

        private fun cursor(rows: List<String>): Cursor {
            var position = -1
            return mock<Cursor> {
                on { columnCount } doAnswer { 1 }
                on { moveToFirst() } doAnswer { position = 0; rows.isNotEmpty() }
                on { moveToNext() } doAnswer { ++position < rows.size }
                on { getType(any()) } doAnswer { Cursor.FIELD_TYPE_STRING }
                on { getString(any()) } doAnswer { rows[position] }
            }.also { cursors += it }
        }
    }
}
