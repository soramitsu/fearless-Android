package jp.co.soramitsu.irohaconnect.impl.domain

import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.irohaconnect.impl.WebVectors
import jp.co.soramitsu.irohaconnect.impl.WebVectors.array
import jp.co.soramitsu.irohaconnect.impl.WebVectors.bytes
import jp.co.soramitsu.irohaconnect.impl.WebVectors.objects
import jp.co.soramitsu.irohaconnect.impl.WebVectors.str
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectCrypto
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectEncoding
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectFrame
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectWire
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Session behaviour around the recorded transcripts: failures, timers, ordering and refusals. */
@OptIn(ExperimentalCoroutinesApi::class)
class IrohaConnectServiceTest {

    private val transcript = WebVectors.root.array("transcripts").objects().first()
    private val events = transcript.array("events").objects()
    private val sid = transcript.bytes("sidHex")
    private val uri = transcript.str("uri")
    private val appFrames = events.filter { it.str("type") == "appFrame" }.map { it.bytes("hex") }

    private class Socket : IrohaConnectSocket {
        val sent = mutableListOf<ByteArray>()
        var closed = false
        var accepting = true
        override fun send(frame: ByteArray): Boolean = accepting.also { if (it) sent += frame }
        override fun close(code: Int, reason: String) {
            closed = true
        }
    }

    private class Accounts(private val failure: IrohaConnectActionError? = null) : IrohaConnectAccountProvider {
        private val derived = IrohaKeyDerivation.deriveAccount(WebVectors.mnemonic)
        val account = IrohaConnectAccount(1, WebVectors.accountAddress, "Sakura", derived.publicKeyHex)
        var signatures = 0
        override suspend fun accounts(networkPrefix: Int) = listOf(account)
        override suspend fun sign(
            account: IrohaConnectAccount,
            networkPrefix: Int,
            message: ByteArray
        ): IrohaConnectSignature {
            failure?.let { throw IrohaConnectActionException(it) }
            signatures += 1
            return IrohaConnectSignature(derived.publicKeyHex, IrohaConnectCrypto.ed25519Sign(derived.privateKey, message))
        }
    }

    private class Harness(scope: TestScope, val accounts: Accounts = Accounts()) {
        val socket = Socket()
        var listener: IrohaConnectSocketListener? = null
        val walletPrivate: ByteArray = WebVectors.root.getAsJsonObject("keys").bytes("walletPrivateHex")
        val service = IrohaConnectService(
            scope = scope.backgroundScope,
            socketFactory = { _, _, socketListener ->
                listener = socketListener
                socketListener.onOpen()
                socket
            },
            accountProvider = accounts,
            now = { scope.testScheduler.currentTime },
            makeRequestId = { "request" },
            makeWalletKeyPair = { walletPrivate.copyOf() to IrohaConnectCrypto.x25519PublicKey(walletPrivate) },
            makePingNonce = { 41 }
        )
    }

    private suspend fun TestScope.act(block: suspend () -> Unit) {
        // A failed action must reach the caller, not the background scope (which would fail the test on its own).
        val job = backgroundScope.async { runCatching { block() } }
        runCurrent()
        job.await().getOrThrow()
    }

    private suspend fun TestScope.connected(harness: Harness) {
        act { assertNull(harness.service.connect(uri)) }
        harness.listener!!.onBinary(appFrames[0])
        runCurrent()
        act { harness.service.approveSession(WebVectors.accountAddress) }
        assertEquals(IrohaConnectPhase.CONNECTED, harness.service.snapshot.value.phase)
    }

    @Test
    fun shouldRefuseBadLinksWithoutTouchingTheSession() = runTest {
        val harness = Harness(this)
        act { assertEquals(IrohaConnectFailure.INVALID_LINK, harness.service.connect("iroha://connect?sid=x")) }
        act {
            assertEquals(IrohaConnectFailure.RETIRED_LINK, harness.service.connect(WebVectors.root.array("links").objects()[3].str("uri")))
        }
        act {
            assertEquals(IrohaConnectFailure.WRONG_NODE, harness.service.connect(WebVectors.root.array("links").objects()[4].str("uri")))
        }
        act {
            assertEquals(IrohaConnectFailure.UNKNOWN_NETWORK, harness.service.connect(WebVectors.root.array("links").objects()[6].str("uri")))
        }
        act {
            assertEquals(IrohaConnectFailure.NETWORK_UNAVAILABLE, harness.service.connect(WebVectors.root.array("links").objects()[7].str("uri")))
        }
        assertEquals(IrohaConnectPhase.IDLE, harness.service.snapshot.value.phase)
        assertNull(harness.listener)
    }

    @Test
    fun shouldKeepTheJoinedSession_whenTheSameLinkArrivesAgain() = runTest {
        val harness = Harness(this)
        connected(harness)
        val socket = harness.socket
        act { assertNull(harness.service.connect(uri)) }
        assertEquals(IrohaConnectPhase.CONNECTED, harness.service.snapshot.value.phase)
        assertTrue(!socket.closed)
    }

    @Test
    fun shouldFailWhenTheDappNeverOpensTheSession() = runTest {
        val harness = Harness(this)
        act { harness.service.connect(uri) }
        assertEquals(IrohaConnectPhase.CONNECTING, harness.service.snapshot.value.phase)
        advanceTimeBy(IrohaConnectService.CONNECT_TIMEOUT_MS)
        runCurrent()
        assertEquals(IrohaConnectPhase.ERROR, harness.service.snapshot.value.phase)
        assertEquals(IrohaConnectFailure.OPEN_TIMEOUT, harness.service.snapshot.value.failure)
        assertTrue(harness.socket.closed)
        act { harness.service.clearMessages() }
        assertEquals(IrohaConnectSnapshot(), harness.service.snapshot.value)
    }

    @Test
    fun shouldCloseTheSessionOnTextFramesRelayFailuresAndClosedSockets() = runTest {
        listOf<(IrohaConnectSocketListener) -> Unit>(
            { it.onText() },
            { it.onFailure(IllegalStateException("down")) },
            { it.onClosed(1006) }
        ).zip(listOf(IrohaConnectFailure.PROTOCOL_ERROR, IrohaConnectFailure.RELAY_UNREACHABLE, IrohaConnectFailure.SESSION_ENDED))
            .forEach { (event, failure) ->
                val harness = Harness(this)
                act { harness.service.connect(uri) }
                event(harness.listener!!)
                runCurrent()
                assertEquals(failure, harness.service.snapshot.value.failure)
                assertTrue(harness.socket.closed)
            }
    }

    @Test
    fun shouldCloseTheSessionOnReplayedOutOfOrderOrForeignFrames() = runTest {
        // Data before the Open frame.
        Harness(this).let { harness ->
            act { harness.service.connect(uri) }
            harness.listener!!.onBinary(appFrames[1])
            runCurrent()
            assertEquals(IrohaConnectFailure.PROTOCOL_ERROR, harness.service.snapshot.value.failure)
        }
        // The Open frame replayed.
        Harness(this).let { harness ->
            connected(harness)
            harness.listener!!.onBinary(appFrames[0])
            runCurrent()
            assertEquals(IrohaConnectFailure.PROTOCOL_ERROR, harness.service.snapshot.value.failure)
        }
        // A gap in the app's sequence.
        Harness(this).let { harness ->
            connected(harness)
            harness.listener!!.onBinary(appFrames[2])
            runCurrent()
            assertEquals(IrohaConnectFailure.PROTOCOL_ERROR, harness.service.snapshot.value.failure)
        }
        // A wallet-direction frame echoed back by a hostile relay.
        Harness(this).let { harness ->
            connected(harness)
            harness.listener!!.onBinary(IrohaConnectWire.encodePingFrame(sid, 1, 1))
            runCurrent()
            assertEquals(IrohaConnectFailure.PROTOCOL_ERROR, harness.service.snapshot.value.failure)
        }
    }

    @Test
    fun shouldSendSequencedConnectPings_andFailWhenThePingCannotBeSent() = runTest {
        val harness = Harness(this)
        connected(harness)
        advanceTimeBy(IrohaConnectService.KEEP_ALIVE_INTERVAL_MS)
        runCurrent()
        val ping = IrohaConnectWire.decodeFrame(harness.socket.sent.last(), sid) as IrohaConnectFrame.Control
        assertEquals(2L, ping.sequence)
        advanceTimeBy(IrohaConnectService.KEEP_ALIVE_INTERVAL_MS)
        runCurrent()
        assertEquals(3L, (IrohaConnectWire.decodeFrame(harness.socket.sent.last(), sid) as IrohaConnectFrame.Control).sequence)
        assertEquals(IrohaConnectPhase.CONNECTED, harness.service.snapshot.value.phase)
        harness.socket.accepting = false
        advanceTimeBy(IrohaConnectService.KEEP_ALIVE_INTERVAL_MS)
        runCurrent()
        assertEquals(IrohaConnectFailure.KEEPALIVE_FAILED, harness.service.snapshot.value.failure)
    }

    @Test
    fun shouldExpireAnUnansweredRequestAndRefuseASecondOne() = runTest {
        val harness = Harness(this)
        connected(harness)
        harness.listener!!.onBinary(appFrames[1])
        runCurrent()
        assertEquals(IrohaConnectPhase.REQUEST_APPROVAL, harness.service.snapshot.value.phase)
        // The next app frame in the recording is the pong; the one after it is a second transaction request.
        harness.listener!!.onBinary(appFrames[2])
        harness.listener!!.onBinary(appFrames[3])
        runCurrent()
        assertEquals(IrohaConnectNoticeCode.REQUEST_IN_PROGRESS, harness.service.snapshot.value.notice?.code)
        assertEquals(IrohaConnectPhase.REQUEST_APPROVAL, harness.service.snapshot.value.phase)
        advanceTimeBy(IrohaConnectService.SIGNING_REQUEST_TTL_MS)
        runCurrent()
        assertEquals(IrohaConnectPhase.CONNECTED, harness.service.snapshot.value.phase)
        assertEquals(IrohaConnectNoticeCode.REQUEST_EXPIRED, harness.service.snapshot.value.notice?.code)
        // Only the session approval was ever signed.
        assertEquals(1, harness.accounts.signatures)
        try {
            act { harness.service.approveRequest("request") }
            fail("an expired request must not be signed")
        } catch (error: IrohaConnectActionException) {
            assertEquals(IrohaConnectActionError.NOT_AVAILABLE, error.error)
        }
    }

    @Test
    fun shouldKeepTheSessionWaiting_whenSigningTheApprovalFails() = runTest {
        val harness = Harness(this, Accounts(IrohaConnectActionError.WALLET_LOCKED))
        act { harness.service.connect(uri) }
        harness.listener!!.onBinary(appFrames[0])
        runCurrent()
        try {
            act { harness.service.approveSession(WebVectors.accountAddress) }
            fail("a locked wallet must not approve")
        } catch (error: IrohaConnectActionException) {
            assertEquals(IrohaConnectActionError.WALLET_LOCKED, error.error)
        }
        assertEquals(IrohaConnectPhase.SESSION_APPROVAL, harness.service.snapshot.value.phase)
        assertTrue(harness.socket.sent.isEmpty())
        try {
            act { harness.service.approveSession("someone else") }
            fail("only a listed account can be connected")
        } catch (error: IrohaConnectActionException) {
            assertEquals(IrohaConnectActionError.ACCOUNT_UNAVAILABLE, error.error)
        }
    }

    @Test
    fun shouldShowOnlySafeDappOrigins() {
        assertEquals("https://hanami.example/market", IrohaConnectService.normalizeAppUrl("https://hanami.example/market"))
        assertEquals("http://localhost:8080/", IrohaConnectService.normalizeAppUrl("http://localhost:8080/"))
        listOf(null, "http://example.org", "https://user:pw@example.org", "javascript:alert(1)", "not a url", "file:///etc").forEach {
            assertNull(it, IrohaConnectService.normalizeAppUrl(it))
        }
        assertEquals("0e", IrohaConnectEncoding.hex(byteArrayOf(14)))
    }
}
