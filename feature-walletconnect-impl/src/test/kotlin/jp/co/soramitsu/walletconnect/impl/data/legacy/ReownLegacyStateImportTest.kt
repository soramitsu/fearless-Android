package jp.co.soramitsu.walletconnect.impl.data.legacy

import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.bytes
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.passphrase
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.section
import jp.co.soramitsu.walletconnect.impl.protocol.WcLimits
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredMetadata
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredSession
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredSessionNamespace
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredState
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredVerify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReownLegacyStateImportTest {

    private val reown = section("reown")
    private val now = reown["now"].asLong
    private val sessions = reown.getAsJsonArray("sessions").map { it.asJsonObject }.associateBy { it["name"].asString }
    private fun topic(name: String) = sessions.getValue(name)["topic"].asString

    private val snapshot by lazy {
        val decoder = SqlCipherDatabaseDecoder()
        ReownLegacySnapshotReader().read(
            ReownLegacySources(
                keyChain = ReownFixtureSource.fixtureKeyChain(),
                signDatabase = SqliteDatabaseImage(decoder.decode(LegacyDatabaseFiles(bytes("reown-sign.db")), passphrase("reown"))),
                coreDatabase = SqliteDatabaseImage(decoder.decode(LegacyDatabaseFiles(bytes("reown-core.db")), passphrase("reown")))
            ),
            now
        )
    }

    @Test
    fun `the sessions the SDK showed as connected come over, settled and with their keys`() {
        val (state, summary) = ReownLegacyStateImport.merge(WcStoredState(), snapshot, now)

        assertEquals(listOf(topic("alpha"), topic("beta")), state.sessions.map { it.topic })
        val alpha = state.sessions.first()
        assertEquals(sessions.getValue("alpha")["sym"].asString, alpha.symKey)
        assertEquals(sessions.getValue("alpha")["pairing"].asString, alpha.pairingTopic)
        assertTrue(alpha.acknowledged)
        assertNull(alpha.settleRequestId)
        assertEquals(listOf("eip155:1", "eip155:137"), alpha.namespaces.getValue("eip155").chains)
        assertEquals(listOf("eth_sendTransaction", "personal_sign"), alpha.requiredNamespaces.getValue("eip155").methods)
        assertEquals(listOf("eip155:137"), alpha.optionalNamespaces.getValue("eip155").chains)
        assertEquals("Alpha Swap", alpha.peer.name)
        assertEquals(listOf("https://alpha.example/a.png", "https://alpha.example/b.png"), alpha.peer.icons)
        assertEquals(2, summary.sessions)
        assertTrue(ReownSkipReason.SESSION_NOT_SETTLED in summary.skipped)
    }

    @Test
    fun `live pairings and the relay identity come over`() {
        val (state, summary) = ReownLegacyStateImport.merge(WcStoredState(), snapshot, now)

        assertEquals(1, state.pairings.size)
        assertTrue(state.pairings.single().active)
        assertEquals("Alpha Swap", state.pairings.single().peer!!.name)
        assertEquals(reown["identitySeed"].asString, state.clientSeed)
        assertTrue(summary.identity)
        assertEquals(1, summary.pairings)
    }

    @Test
    fun `an unanswered request comes over with its Verify result`() {
        val (state, summary) = ReownLegacyStateImport.merge(WcStoredState(), snapshot, now)

        val request = state.requests.single()
        assertEquals(topic("alpha"), request.topic)
        assertEquals(1794000000123456L, request.id)
        assertEquals("eip155:1", request.chainId)
        assertEquals("personal_sign", request.method)
        assertEquals("""["0x48656c6c6f","0x0000000000000000000000000000000000000001"]""", request.params)
        assertEquals(now + 300, request.expiry)
        assertEquals(WcStoredVerify("VALID", "https://alpha.example", "https://alpha.example", false), request.verify)
        assertEquals(1, summary.requests)
    }

    @Test
    fun `importing twice changes nothing and the client's own records always win`() {
        val (first, _) = ReownLegacyStateImport.merge(WcStoredState(), snapshot, now)
        val (second, again) = ReownLegacyStateImport.merge(first, snapshot, now)
        assertEquals(first, second)
        assertEquals(0, again.sessions + again.pairings + again.requests)
        assertFalse(again.identity)

        val own = first.sessions.first().copy(symKey = "11".repeat(32), peer = WcStoredMetadata("Mine", "", "", emptyList()))
        val withOwn = WcStoredState(clientSeed = "22".repeat(32), sessions = listOf(own))
        val (merged, _) = ReownLegacyStateImport.merge(withOwn, snapshot, now)
        assertEquals(own, merged.sessions.single { it.topic == own.topic })
        assertEquals("22".repeat(32), merged.clientSeed)
    }

    @Test
    fun `nothing expired comes over`() {
        val later = sessions.getValue("alpha")["expiry"].asLong + 1

        val (state, summary) = ReownLegacyStateImport.merge(WcStoredState(), snapshot, later)

        assertTrue(state.sessions.isEmpty() && state.pairings.isEmpty() && state.requests.isEmpty())
        assertTrue(ReownSkipReason.SESSION_EXPIRED in summary.skipped)
        assertTrue(ReownSkipReason.PAIRING_EXPIRED in summary.skipped)
    }

    @Test
    fun `a request must be readable, allowed by its session and still open`() {
        val alpha = snapshot.sessions.first { it.topic == topic("alpha") }
        fun body(
            method: String,
            chainId: String = "eip155:1",
            expiry: String = ",\"expiryTimestamp\":${now + 60}"
        ) =
            """{"id":1,"jsonrpc":"2.0","method":"wc_sessionRequest","params":{"request":{"method":"$method","params":[]$expiry},"chainId":"$chainId"}}"""
        val requests = listOf(
            ReownLegacyRequest(1, alpha.topic, "{not json", null),
            ReownLegacyRequest(2, alpha.topic, body("eth_sign"), null),
            ReownLegacyRequest(3, alpha.topic, body("personal_sign", chainId = "eip155:56"), null),
            ReownLegacyRequest(4, alpha.topic, body("personal_sign", expiry = ""), null),
            ReownLegacyRequest(now * 1_000_000, alpha.topic, body("personal_sign", expiry = ""), null),
            ReownLegacyRequest(6, "f".repeat(64), body("personal_sign"), null)
        )
        val crafted = snapshot.copy(sessions = listOf(alpha), pairings = emptyList(), pendingRequests = requests, skipped = emptyList())

        val (state, summary) = ReownLegacyStateImport.merge(WcStoredState(), crafted, now)

        assertEquals(listOf(now * 1_000_000), state.requests.map { it.id })
        assertEquals("UNKNOWN", state.requests.single().verify.validation)
        assertEquals(
            listOf(
                ReownSkipReason.REQUEST_UNREADABLE,
                ReownSkipReason.REQUEST_NOT_ALLOWED,
                ReownSkipReason.REQUEST_NOT_ALLOWED,
                ReownSkipReason.REQUEST_EXPIRED,
                ReownSkipReason.REQUEST_NOT_ALLOWED
            ),
            summary.skipped
        )
    }

    @Test
    fun `the client's limits apply to what comes over`() {
        val alpha = snapshot.sessions.first { it.topic == topic("alpha") }
        val existing = (1 until WcLimits.MAX_SESSIONS).map { index ->
            WcStoredSession(
                topic = index.toString().padStart(64, '0'),
                symKey = "33".repeat(32),
                selfPublicKey = "44".repeat(32),
                peerPublicKey = "55".repeat(32),
                pairingTopic = "",
                relayProtocol = "irn",
                expiry = now + 100,
                acknowledged = true,
                namespaces = mapOf("eip155" to WcStoredSessionNamespace(null, emptyList(), emptyList(), emptyList())),
                peer = WcStoredMetadata("", "", "", emptyList())
            )
        }
        val crafted = snapshot.copy(
            sessions = listOf(alpha, alpha.copy(topic = "e".repeat(64))),
            pairings = emptyList(),
            pendingRequests = emptyList(),
            skipped = emptyList()
        )

        val (state, summary) = ReownLegacyStateImport.merge(WcStoredState(sessions = existing), crafted, now)

        assertEquals(WcLimits.MAX_SESSIONS, state.sessions.size)
        assertEquals(listOf(ReownSkipReason.LIMIT_REACHED), summary.skipped)
    }

    @Test
    fun `metadata is bounded as a dApp's would be, and a session without any borrows its pairing's`() {
        val alpha = snapshot.sessions.first { it.topic == topic("alpha") }
        val noisy = ReownLegacyMetadata(
            name = "Al\u0000pha",
            description = "d",
            url = "https://alpha.example",
            icons = listOf("javascript:alert(1)", "https://ok.example/i.png") + List(20) { "https://x.example/$it.png" },
            redirect = null
        )
        val pairing = ReownLegacyPairing(alpha.pairingTopic, "66".repeat(32), "irn", now + 100, true, noisy)
        val crafted = snapshot.copy(
            sessions = listOf(alpha.copy(peer = null)),
            pairings = listOf(pairing),
            pendingRequests = emptyList(),
            skipped = emptyList()
        )

        val peer = ReownLegacyStateImport.merge(WcStoredState(), crafted, now).first.sessions.single().peer

        assertEquals("Alpha", peer.name)
        assertEquals(WcLimits.MAX_ICONS, peer.icons.size)
        assertEquals("https://ok.example/i.png", peer.icons.first())

        val unnamed = ReownLegacyStateImport.merge(WcStoredState(), crafted.copy(pairings = emptyList()), now).first.sessions.single().peer
        assertEquals(WcStoredMetadata("", "", "", emptyList(), null), unnamed)
    }

    @Test
    fun `stored state JSON round-trips, and what cannot be read counts as nothing stored`() {
        val (state, _) = ReownLegacyStateImport.merge(WcStoredState(), snapshot, now)

        assertEquals(state, WcStoredStateJson.decodeOrEmpty(WcStoredStateJson.encode(state)))
        assertEquals(WcStoredState(), WcStoredStateJson.decodeOrEmpty(null))
        assertEquals(WcStoredState(), WcStoredStateJson.decodeOrEmpty("{broken"))
        assertEquals(WcStoredState(), WcStoredStateJson.decodeOrEmpty("""{"version":99}"""))
    }
}
