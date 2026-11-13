package jp.co.soramitsu.tonconnect.impl.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TonConnectBridgeEventLedgerTest {

    @Test
    fun shouldNotResume_untilEverySubscribedConnectionHasAnEvent() {
        val ledger = TonConnectBridgeEventLedger(InMemoryStorage())

        assertNull(ledger.resumeEventId(BRIDGE, emptyList()))
        assertNull(ledger.resumeEventId(BRIDGE, listOf(CLIENT_A)))

        ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 200, requestId = 1)

        assertEquals(200L, ledger.resumeEventId(BRIDGE, listOf(CLIENT_A)))
        // A new connection has no event yet: its queued requests must not be skipped.
        assertNull(ledger.resumeEventId(BRIDGE, listOf(CLIENT_A, CLIENT_B)))
    }

    @Test
    fun shouldResumeAfterTheOldestLastEvent_whenSeveralConnectionsAreSubscribed() {
        val ledger = TonConnectBridgeEventLedger(InMemoryStorage())

        ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 300, requestId = 1)
        ledger.recordEvent(BRIDGE, CLIENT_B, eventId = 250)

        assertEquals(250L, ledger.resumeEventId(BRIDGE, listOf(CLIENT_A, CLIENT_B)))
    }

    @Test
    fun shouldDropAReDeliveredEvent_whenItIsNotNewerThanTheLastOne() {
        val ledger = TonConnectBridgeEventLedger(InMemoryStorage())
        assertTrue(ledger.isNewEvent(BRIDGE, CLIENT_A, 100))

        ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 100, requestId = 1)

        assertFalse(ledger.isNewEvent(BRIDGE, CLIENT_A, 99))
        assertFalse(ledger.isNewEvent(BRIDGE, CLIENT_A, 100))
        assertTrue(ledger.isNewEvent(BRIDGE, CLIENT_A, 101))
        assertTrue(ledger.isNewEvent(BRIDGE, CLIENT_B, 1))
    }

    @Test
    fun shouldHandARequestToTheWalletOnce_evenWhenItArrivesAgainWithANewEventId() {
        val ledger = TonConnectBridgeEventLedger(InMemoryStorage())

        assertTrue(ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 100, requestId = 7))
        assertFalse(ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 101, requestId = 7))
        assertTrue(ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 102, requestId = 8))
        // Request ids belong to one dApp session; another connection may reuse them.
        assertTrue(ledger.admitRequest(BRIDGE, CLIENT_B, eventId = 103, requestId = 7))
        assertEquals(102L, ledger.resumeEventId(BRIDGE, listOf(CLIENT_A)))
    }

    @Test
    fun shouldRememberEventsAndRequests_acrossRestarts() {
        val storage = InMemoryStorage()
        TonConnectBridgeEventLedger(storage).admitRequest(BRIDGE, CLIENT_A, eventId = 500, requestId = 3)

        val restarted = TonConnectBridgeEventLedger(storage)

        assertEquals(500L, restarted.resumeEventId(BRIDGE, listOf(CLIENT_A)))
        assertFalse(restarted.isNewEvent(BRIDGE, CLIENT_A, 500))
        assertFalse(restarted.admitRequest(BRIDGE, CLIENT_A, eventId = 501, requestId = 3))
    }

    @Test
    fun shouldAdvanceTheLastEvent_whenAnEventCarriesNoRequest() {
        val ledger = TonConnectBridgeEventLedger(InMemoryStorage())

        ledger.recordEvent(BRIDGE, CLIENT_A, eventId = 40)
        ledger.recordEvent(BRIDGE, CLIENT_A, eventId = 30)

        assertEquals(40L, ledger.resumeEventId(BRIDGE, listOf(CLIENT_A)))
        assertTrue(ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 41, requestId = 1))
    }

    @Test
    fun shouldTreatClientIdsCaseInsensitively_becauseTheyAreHex() {
        val ledger = TonConnectBridgeEventLedger(InMemoryStorage())

        ledger.admitRequest(BRIDGE, CLIENT_A.uppercase(), eventId = 10, requestId = 1)

        assertFalse(ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 11, requestId = 1))
        assertEquals(11L, ledger.resumeEventId(BRIDGE, listOf(CLIENT_A.uppercase())))
    }

    @Test
    fun shouldForgetEventIdsButKeepRequests_whenTheBridgeChanges() {
        val ledger = TonConnectBridgeEventLedger(InMemoryStorage())
        ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 9_000, requestId = 5)

        assertNull(ledger.resumeEventId(OTHER_BRIDGE, listOf(CLIENT_A)))
        assertTrue(ledger.isNewEvent(OTHER_BRIDGE, CLIENT_A, 1))
        assertFalse(ledger.admitRequest(OTHER_BRIDGE, CLIENT_A, eventId = 1, requestId = 5))
        assertEquals(1L, ledger.resumeEventId(OTHER_BRIDGE, listOf(CLIENT_A)))
    }

    @Test
    fun shouldKeepABoundedNumberOfRequestIds_perConnection() {
        val ledger = TonConnectBridgeEventLedger(InMemoryStorage())
        val limit = TonConnectBridgeEventLedger.MAX_REQUEST_IDS

        (0..limit).forEach { requestId ->
            ledger.admitRequest(BRIDGE, CLIENT_A, eventId = requestId + 1L, requestId = requestId.toLong())
        }

        assertFalse(ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 1_000, requestId = limit.toLong()))
        assertFalse(ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 1_001, requestId = 1))
        // The oldest id was forgotten; the per-connection event id still drops its re-delivery.
        assertTrue(ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 1_002, requestId = 0))
    }

    @Test
    fun shouldKeepABoundedNumberOfConnections_evictingTheLeastRecentlyUsed() {
        val storage = InMemoryStorage()
        val ledger = TonConnectBridgeEventLedger(storage)
        val clients = (0..TonConnectBridgeEventLedger.MAX_CONNECTIONS).map { index -> clientId(index) }

        clients.forEachIndexed { index, clientId ->
            ledger.admitRequest(BRIDGE, clientId, eventId = index + 1L, requestId = 1)
        }

        assertNull(ledger.resumeEventId(BRIDGE, listOf(clients.first())))
        assertEquals(clients.size.toLong(), ledger.resumeEventId(BRIDGE, listOf(clients.last())))
        val restarted = TonConnectBridgeEventLedger(storage)
        assertNull(restarted.resumeEventId(BRIDGE, listOf(clients.first())))
        assertEquals(2L, restarted.resumeEventId(BRIDGE, listOf(clients[1])))
    }

    @Test
    fun shouldStartEmpty_whenStoredStateIsUnreadable() {
        listOf("", "garbage", "v2\n$CLIENT_A|0000000000000000|1|", "v1\nnot|a|valid|line").forEach { stored ->
            val ledger = TonConnectBridgeEventLedger(InMemoryStorage(stored))

            assertNull(stored, ledger.resumeEventId(BRIDGE, listOf(CLIENT_A)))
            assertTrue(stored, ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 1, requestId = 1))
        }
    }

    @Test
    fun shouldKeepReadableEntries_whenOtherStoredEntriesAreDamaged() {
        val storage = InMemoryStorage()
        TonConnectBridgeEventLedger(storage).admitRequest(BRIDGE, CLIENT_A, eventId = 77, requestId = 2)
        val damaged = InMemoryStorage(storage.value + "\nzz|broken|x|y\n$CLIENT_B|short|1|")

        val ledger = TonConnectBridgeEventLedger(damaged)

        assertEquals(77L, ledger.resumeEventId(BRIDGE, listOf(CLIENT_A)))
        assertFalse(ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 78, requestId = 2))
        assertNull(ledger.resumeEventId(BRIDGE, listOf(CLIENT_B)))
    }

    @Test
    fun shouldStillDropDuplicatesInThisProcess_whenDurableWritesFail() {
        listOf(InMemoryStorage(failWrites = true), InMemoryStorage(throwOnWrite = true)).forEach { storage ->
            val ledger = TonConnectBridgeEventLedger(storage)

            assertTrue(ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 1, requestId = 9))
            assertFalse(ledger.admitRequest(BRIDGE, CLIENT_A, eventId = 2, requestId = 9))
            assertFalse(ledger.isNewEvent(BRIDGE, CLIENT_A, 2))
        }
    }

    @Test
    fun shouldNotRecordClientIds_thatAreNotHex() {
        val storage = InMemoryStorage()
        val ledger = TonConnectBridgeEventLedger(storage)

        assertTrue(ledger.admitRequest(BRIDGE, "not|hex", eventId = 1, requestId = 1))
        assertTrue(ledger.admitRequest(BRIDGE, "not|hex", eventId = 2, requestId = 1))
        assertNull(storage.value)
    }

    private class InMemoryStorage(
        initial: String? = null,
        private val failWrites: Boolean = false,
        private val throwOnWrite: Boolean = false
    ) : TonConnectBridgeEventLedger.Storage {
        var value: String? = initial
            private set

        override fun read(): String? = value

        override fun write(value: String): Boolean {
            check(!throwOnWrite) { "Disk full" }
            if (failWrites) return false
            this.value = value
            return true
        }
    }

    private companion object {
        const val BRIDGE = "https://bridge.tonapi.io/bridge"
        const val OTHER_BRIDGE = "https://connect.example.org/bridge"
        val CLIENT_A = "a".repeat(64)
        val CLIENT_B = "b".repeat(64)

        fun clientId(index: Int): String = index.toString(16).padStart(64, '0')
    }
}
