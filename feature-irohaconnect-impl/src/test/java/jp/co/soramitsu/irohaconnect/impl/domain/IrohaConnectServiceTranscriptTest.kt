package jp.co.soramitsu.irohaconnect.impl.domain

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.irohaconnect.impl.WebVectors
import jp.co.soramitsu.irohaconnect.impl.WebVectors.array
import jp.co.soramitsu.irohaconnect.impl.WebVectors.bytes
import jp.co.soramitsu.irohaconnect.impl.WebVectors.objects
import jp.co.soramitsu.irohaconnect.impl.WebVectors.str
import jp.co.soramitsu.irohaconnect.impl.WebVectors.strOrNull
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectControl
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectCrypto
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectEncoding
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectFrame
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectWire
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays sessions recorded between the upstream iroha-js app SDK and the browser wallet: the Android wallet receives
 * the same app frames, takes the same actions, and must send byte-identical frames and reach the same states.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IrohaConnectServiceTranscriptTest {

    private class FakeSocket : IrohaConnectSocket {
        val sent = ArrayDeque<ByteArray>()
        var closed = false
        override fun send(frame: ByteArray): Boolean {
            if (closed) return false
            sent.addLast(frame)
            return true
        }

        override fun close(code: Int, reason: String) {
            closed = true
        }
    }

    /** Signs with the key the same mnemonic derives in the browser wallet, through the common Iroha derivation. */
    private class MnemonicAccounts : IrohaConnectAccountProvider {
        private val derived = IrohaKeyDerivation.deriveAccount(WebVectors.mnemonic)
        val account = IrohaConnectAccount(1, WebVectors.accountAddress, "Sakura", derived.publicKeyHex)

        override suspend fun accounts(networkPrefix: Int) = listOf(account)

        override suspend fun sign(
            account: IrohaConnectAccount,
            networkPrefix: Int,
            message: ByteArray
        ) = IrohaConnectSignature(derived.publicKeyHex, IrohaConnectCrypto.ed25519Sign(derived.privateKey, message))
    }

    @Test
    fun shouldDeriveTheBrowserWalletsIrohaKey_whenGivenTheVectorMnemonic() {
        assertEquals(WebVectors.accountPublicKeyHex, IrohaKeyDerivation.deriveAccount(WebVectors.mnemonic).publicKeyHex)
    }

    @Test
    fun shouldReplayEveryRecordedSessionByteForByte() {
        WebVectors.root.array("transcripts").objects().forEach(::replay)
    }

    private fun replay(transcript: JsonObject) = runTest {
        val name = transcript.str("name")
        val sid = transcript.bytes("sidHex")
        val events = transcript.array("events").objects()
        val walletPrivate = transcript.bytes("walletPrivateHex")
        // The recorded keepalive ping carries the browser wallet's random nonce; start just below it.
        val pingNonce = events.firstOrNull { it.str("type") == "keepAlive" }?.let { marker ->
            val pingHex = events[events.indexOf(marker) + 1].str("hex")
            ((IrohaConnectWire.decodeFrame(IrohaConnectEncoding.unhex(pingHex), sid) as IrohaConnectFrame.Control).control as IrohaConnectControl.Ping).nonce - 1
        } ?: 0L
        var requestCounter = 0
        var clock = 1_795_000_000_000L
        val socket = FakeSocket()
        var listener: IrohaConnectSocketListener? = null
        val opened = mutableListOf<Pair<String, String>>()
        val service = IrohaConnectService(
            scope = backgroundScope,
            socketFactory = { url, protocol, socketListener ->
                opened += url to protocol
                listener = socketListener
                socketListener.onOpen()
                socket
            },
            accountProvider = MnemonicAccounts(),
            now = { clock },
            makeRequestId = { "request-${++requestCounter}" },
            makeWalletKeyPair = { walletPrivate.copyOf() to IrohaConnectCrypto.x25519PublicKey(walletPrivate) },
            makePingNonce = { pingNonce }
        )
        val settle = { runCurrent() }
        var compared = 0

        events.forEachIndexed { index, event ->
            val where = "$name #$index ${event.str("type")}"
            when (event.str("type")) {
                "action" -> {
                    if (event.str("name") == "approveRequest") clock += 1000
                    val job = backgroundScope.async { runCatching { act(service, event) } }
                    settle()
                    job.await().getOrThrow()
                }
                "openSocket" -> assertEquals(where, event.str("url") to event.str("protocol"), opened.single())
                "appFrame" -> {
                    requireNotNull(listener).onBinary(event.bytes("hex"))
                    settle()
                }
                "walletFrame" -> {
                    compared += 1
                    assertTrue("$where: the Android wallet sent nothing", socket.sent.isNotEmpty())
                    assertEquals(where, event.str("hex"), IrohaConnectEncoding.hex(socket.sent.removeFirst()))
                }
                "keepAlive" -> {
                    advanceTimeBy(IrohaConnectService.KEEP_ALIVE_INTERVAL_MS)
                    settle()
                }
                "walletClosedSocket" -> assertTrue(where, socket.closed)
                "snapshot" -> assertSnapshot(where, event, service.snapshot.value)
                else -> Unit
            }
        }
        assertTrue("$name: unexpected extra frames", socket.sent.isEmpty())
        assertEquals(name, events.count { it.str("type") == "walletFrame" }, compared)
        assertTrue(name, compared > 0)
    }

    private suspend fun act(service: IrohaConnectService, event: JsonObject) {
        when (event.str("name")) {
            "connect" -> assertEquals(null, service.connect(event.str("arg")))
            "approveSession" -> service.approveSession(event.str("arg"))
            "rejectSession" -> service.rejectSession()
            "approveRequest" -> service.approveRequest(event.str("arg"))
            "rejectRequest" -> service.rejectRequest(event.str("arg"))
            "disconnect" -> service.disconnect()
            else -> error("unknown action ${event.str("name")}")
        }
    }

    private fun assertSnapshot(
        where: String,
        expected: JsonObject,
        actual: IrohaConnectSnapshot
    ) {
        assertEquals(where, expected.str("phase").uppercase().replace('-', '_'), actual.phase.name)
        assertEquals(where, expected.strOrNull("notice")?.uppercase(), actual.notice?.code?.name)
        assertEquals(where, expected.strOrNull("selectedAccountId"), actual.selectedAccountId)
        assertEquals(where, expected.strOrNull("requestId"), actual.request?.requestId)
        expected.get("session")?.takeUnless(JsonElement::isJsonNull)?.asJsonObject?.let { session ->
            assertEquals(where, session.str("appName"), actual.session?.appName)
            val permissions = session.get("permissions")?.takeUnless(JsonElement::isJsonNull)?.asJsonObject
            assertEquals(where, permissions?.array("methods")?.map { it.asString }, actual.session?.permissions?.methods)
        }
        if (expected.str("phase") == "session-approval") {
            assertEquals(where, expected.array("accounts").map { it.asString }, actual.accounts.map { it.address })
        }
        expected.get("review")?.takeUnless(JsonElement::isJsonNull)?.asJsonObject?.let { review ->
            val actualReview = requireNotNull(actual.request).review
            assertEquals(where, review.str("payloadHashHex"), actualReview.payloadHashHex)
            assertEquals(where, review.str("kind"), actualReview.kind.wireName)
        }
    }
}
