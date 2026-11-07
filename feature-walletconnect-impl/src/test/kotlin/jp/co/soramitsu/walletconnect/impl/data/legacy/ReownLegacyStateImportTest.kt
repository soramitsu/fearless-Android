package jp.co.soramitsu.walletconnect.impl.data.legacy

import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.bytes
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.passphrase
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.section
import jp.co.soramitsu.walletconnect.impl.protocol.WcLimits
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredMetadata
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredSession
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredSessionNamespace
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReownLegacyStateImportTest {

    private val reown = section("reown")
    private val now = reown["now"].asLong
    private val sessions = reown.getAsJsonArray("sessions").map { it.asJsonObject }.associateBy { it["name"].asString }

    private val snapshot by lazy {
        val decoder = SqlCipherDatabaseDecoder()
        ReownLegacySnapshotReader().read(
            ReownLegacySources(
                keyChain = ReownFixtureSource.fixtureKeyChain(),
                signDatabase = SqliteDatabaseImage(decoder.decode(LegacyDatabaseFiles(bytes("reown-sign.db")), passphrase("reown"))),
                coreDatabase = SqliteDatabaseImage(decoder.decode(LegacyDatabaseFiles(bytes("reown-core.db")), passphrase("reown")))
            )
        )
    }

    private fun topic(name: String) = sessions.getValue(name)["topic"].asString

    @Test
    fun `every usable session comes over with its keys, live ones first`() {
        val (state, summary) = ReownLegacyStateImport.merge(WcStoredState(), snapshot, now)

        assertEquals(listOf("alpha", "beta", "unsettled", "expired").map(::topic), state.sessions.map { it.topic })
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
        assertFalse(state.sessions.single { it.topic == topic("unsettled") }.acknowledged)
        assertEquals(4, summary.sessions)
        assertEquals(listOf(ReownSkipReason.SESSION_INCOMPLETE), summary.skipped)
        assertTrue(summary.isComplete)
    }

    @Test
    fun `a session that expired while the app was off comes over, for the client to report`() {
        val (state, summary) = ReownLegacyStateImport.merge(WcStoredState(), snapshot, now)

        val expired = state.sessions.single { it.topic == topic("expired") }
        assertEquals(sessions.getValue("expired")["expiry"].asLong, expired.expiry)
        assertTrue(expired.expiry <= now)
        assertEquals(1, summary.expiredSessions)
    }

    @Test
    fun `pairings and the relay identity come over, and nothing in flight does`() {
        val (state, summary) = ReownLegacyStateImport.merge(WcStoredState(), snapshot, now)

        assertEquals(2, state.pairings.size)
        assertTrue(state.pairings.first().active)
        assertEquals("Alpha Swap", state.pairings.first().peer!!.name)
        assertEquals(reown["identitySeed"].asString, state.clientSeed)
        assertTrue(summary.identity)
        assertEquals(2, summary.pairings)
        assertTrue(state.requests.isEmpty() && state.proposals.isEmpty())
    }

    @Test
    fun `importing twice changes nothing and the client's own records always win`() {
        val (first, _) = ReownLegacyStateImport.merge(WcStoredState(), snapshot, now)
        val (second, again) = ReownLegacyStateImport.merge(first, snapshot, now)
        assertEquals(first, second)
        assertEquals(0, again.sessions + again.pairings)
        assertFalse(again.identity)

        val own = first.sessions.first().copy(symKey = "11".repeat(32), peer = WcStoredMetadata("Mine", "", "", emptyList()))
        val withOwn = WcStoredState(clientSeed = "22".repeat(32), sessions = listOf(own))
        val (merged, _) = ReownLegacyStateImport.merge(withOwn, snapshot, now)
        assertEquals(own, merged.sessions.single { it.topic == own.topic })
        assertEquals("22".repeat(32), merged.clientSeed)
    }

    @Test
    fun `a live session over the client's limit makes the import incomplete, an expired one does not`() {
        val alpha = snapshot.sessions.first { it.topic == topic("alpha") }
        val expired = snapshot.sessions.first { it.topic == topic("expired") }
        val existing = (1 until WcLimits.MAX_SESSIONS).map { index -> storedSession(index.toString().padStart(64, '0')) }
        fun crafted(vararg legacy: ReownLegacySession) =
            snapshot.copy(sessions = legacy.toList(), pairings = emptyList(), skipped = emptyList())

        val (full, overLimit) = ReownLegacyStateImport.merge(WcStoredState(sessions = existing), crafted(alpha, alpha.copy(topic = "e".repeat(64))), now)
        assertEquals(WcLimits.MAX_SESSIONS, full.sessions.size)
        assertEquals(listOf(ReownSkipReason.LIMIT_REACHED), overLimit.skipped)
        assertFalse(overLimit.isComplete)

        val (_, onlyExpiredLeftOut) = ReownLegacyStateImport.merge(WcStoredState(sessions = existing), crafted(alpha, expired), now)
        assertEquals(listOf(ReownSkipReason.SESSION_EXPIRED), onlyExpiredLeftOut.skipped)
        assertTrue(onlyExpiredLeftOut.isComplete)
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
        val crafted = snapshot.copy(sessions = listOf(alpha.copy(peer = null)), pairings = listOf(pairing), skipped = emptyList())

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

    private fun storedSession(topic: String) = WcStoredSession(
        topic = topic,
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
