package jp.co.soramitsu.walletconnect.impl.data.legacy

import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.bytes
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.passphrase
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReownLegacySnapshotReaderTest {

    private val reown = section("reown")
    private val now = reown["now"].asLong
    private val keyChain: Map<String, String> = reown.getAsJsonObject("keychain").entrySet().associate { it.key to it.value.asString }
    private val sessions = reown.getAsJsonArray("sessions").map { it.asJsonObject }.associateBy { it["name"].asString }
    private val pairings = reown.getAsJsonArray("pairings").map { it.asJsonObject }.associateBy { it["name"].asString }

    private val sources by lazy {
        val decoder = SqlCipherDatabaseDecoder()
        ReownLegacySources(
            keyChain = keyChain,
            signDatabase = SqliteDatabaseImage(decoder.decode(LegacyDatabaseFiles(bytes("reown-sign.db")), passphrase("reown"))),
            coreDatabase = SqliteDatabaseImage(decoder.decode(LegacyDatabaseFiles(bytes("reown-core.db")), passphrase("reown")))
        )
    }

    @Test
    fun `live sessions come back with their keys, namespaces and dApp metadata`() {
        val snapshot = ReownLegacySnapshotReader().read(sources, now)

        assertEquals(listOf("alpha", "beta", "unsettled").map { sessions.getValue(it)["topic"].asString }, snapshot.sessions.map { it.topic })
        val alpha = snapshot.sessions.first()
        val expected = sessions.getValue("alpha")
        assertEquals(expected["sym"].asString, alpha.symKey)
        assertEquals(expected["self"].asString, alpha.selfPublicKey)
        assertEquals(expected["peer"].asString, alpha.peerPublicKey)
        assertEquals(expected["pairing"].asString, alpha.pairingTopic)
        assertEquals(expected["expiry"].asLong, alpha.expiry)
        assertEquals("irn", alpha.relayProtocol)
        assertTrue(alpha.acknowledged)
        assertEquals(setOf("eip155", "polkadot"), alpha.namespaces.keys)
        assertEquals(listOf("eip155:1", "eip155:137"), alpha.namespaces.getValue("eip155").chains)
        assertEquals(
            listOf("eth_sendTransaction", "personal_sign", "eth_signTypedData_v4"),
            alpha.namespaces.getValue("eip155").methods
        )
        assertEquals(emptyList<String>(), alpha.namespaces.getValue("polkadot").events)
        assertEquals(listOf("eth_sendTransaction", "personal_sign"), alpha.requiredNamespaces.getValue("eip155").methods)
        assertEquals(listOf("eip155:137"), alpha.optionalNamespaces.getValue("eip155").chains)
        assertEquals(
            ReownLegacyMetadata(
                name = "Alpha Swap",
                description = "Swaps",
                url = "https://alpha.example",
                icons = listOf("https://alpha.example/a.png", "https://alpha.example/b.png"),
                redirect = "alpha://"
            ),
            alpha.peer
        )
    }

    @Test
    fun `a session without a stored peer key takes it from the key agreement, and unset chains stay null`() {
        val beta = ReownLegacySnapshotReader().read(sources, now).sessions.single { it.topic == sessions.getValue("beta")["topic"].asString }

        assertEquals(sessions.getValue("beta")["peer"].asString, beta.peerPublicKey)
        assertTrue(beta.acknowledged)
        assertNull(beta.namespaces.getValue("eip155:56").chains)
        assertNull(beta.optionalNamespaces.getValue("eip155").chains)
        assertEquals("Beta Lend", beta.peer!!.name)
        assertEquals(emptyList<String>(), beta.peer!!.icons)
        assertNull(beta.peer!!.redirect)
    }

    @Test
    fun `expired sessions and sessions whose key is gone are left behind with a reason code`() {
        val snapshot = ReownLegacySnapshotReader().read(sources, now)

        val topics = snapshot.sessions.map { it.topic }
        listOf("expired", "keyless").forEach { name -> assertTrue(name, sessions.getValue(name)["topic"].asString !in topics) }
        assertEquals(
            listOf(ReownSkipReason.SESSION_EXPIRED, ReownSkipReason.SESSION_INCOMPLETE, ReownSkipReason.PAIRING_EXPIRED),
            snapshot.skipped
        )
    }

    @Test
    fun `a key that does not hash to its session topic is refused`() {
        val alphaTopic = sessions.getValue("alpha")["topic"].asString
        val wrongKey = sessions.getValue("beta")["sym"].asString
        val corrupted = ReownLegacySources(keyChain + (alphaTopic to wrongKey), sources.signDatabase, sources.coreDatabase)

        val snapshot = ReownLegacySnapshotReader().read(corrupted, now)

        assertTrue(snapshot.sessions.none { it.topic == alphaTopic })
    }

    @Test
    fun `live pairings, the relay identity and unanswered requests come back`() {
        val snapshot = ReownLegacySnapshotReader().read(sources, now)

        val live = pairings.getValue("live")
        assertEquals(listOf(live["topic"].asString), snapshot.pairings.map { it.topic })
        assertEquals(live["sym"].asString, snapshot.pairings.single().symKey)
        assertTrue(snapshot.pairings.single().active)
        assertEquals("Alpha Swap", snapshot.pairings.single().peer!!.name)
        assertEquals(reown["identitySeed"].asString, snapshot.clientSeed)

        val request = snapshot.pendingRequests.single()
        assertEquals(1794000000123456L, request.id)
        assertEquals(sessions.getValue("alpha")["topic"].asString, request.topic)
        assertEquals(reown["pendingRequestBody"].asString, request.body)
        assertEquals(ReownLegacyVerify(validation = "VALID", origin = "https://alpha.example", isScam = false), request.verify)
    }

    @Test
    fun `without databases only the identity is read`() {
        val snapshot = ReownLegacySnapshotReader().read(ReownLegacySources(keyChain, null, null), now)

        assertEquals(reown["identitySeed"].asString, snapshot.clientSeed)
        assertTrue(snapshot.sessions.isEmpty() && snapshot.pairings.isEmpty() && snapshot.pendingRequests.isEmpty())
    }

    @Test
    fun `an unreadable identity is skipped, not guessed`() {
        val snapshot = ReownLegacySnapshotReader().read(ReownLegacySources(mapOf("key_did_keypair" to "zz"), null, null), now)

        assertNull(snapshot.clientSeed)
        assertEquals(listOf(ReownSkipReason.IDENTITY_UNREADABLE), snapshot.skipped)
    }

    @Test
    fun `list columns and hex helpers follow the SDK's encoding`() {
        assertNull(null.toList())
        assertEquals(emptyList<String>(), " ".toList())
        assertEquals(listOf("a", "b"), "a,b".toList())
        assertEquals("00ff", byteArrayOf(0, -1).toHex())
        assertEquals("00ff", "00FF".lowercase().hexToBytes().toHex())
        assertTrue("ab".repeat(32).isKeyHex())
        assertTrue(!"ab".repeat(31).isKeyHex())
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(""))
    }
}
