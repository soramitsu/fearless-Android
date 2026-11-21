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
    private val keyChain: Map<String, String> =
        reown.getAsJsonObject("keychain").entrySet().associate { it.key to it.value.asString }
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

    private fun topic(session: String) = sessions.getValue(session)["topic"].asString

    @Test
    fun `sessions come back with their keys, namespaces and dApp metadata`() {
        val snapshot = ReownLegacySnapshotReader().read(sources)

        assertEquals(
            listOf("alpha", "beta", "expired", "unsettled").map { sessions.getValue(it)["topic"].asString },
            snapshot.sessions.map { it.topic }
        )
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
        val beta = ReownLegacySnapshotReader().read(sources).sessions.single { it.topic == topic("beta") }

        assertEquals(sessions.getValue("beta")["peer"].asString, beta.peerPublicKey)
        assertTrue(beta.acknowledged)
        assertNull(beta.namespaces.getValue("eip155:56").chains)
        assertNull(beta.optionalNamespaces.getValue("eip155").chains)
        assertEquals("Beta Lend", beta.peer!!.name)
        assertEquals(emptyList<String>(), beta.peer!!.icons)
        assertNull(beta.peer!!.redirect)
    }

    @Test
    fun `only a session whose key is gone is left behind with a reason code, expired and unsettled ones are read`() {
        val snapshot = ReownLegacySnapshotReader().read(sources)

        assertTrue(sessions.getValue("keyless")["topic"].asString !in snapshot.sessions.map { it.topic })
        assertEquals(listOf(ReownSkipReason.SESSION_INCOMPLETE), snapshot.skipped)
        val expired = snapshot.sessions.single { it.topic == topic("expired") }
        assertEquals(sessions.getValue("expired")["expiry"].asLong, expired.expiry)
        assertEquals(false, snapshot.sessions.single { it.topic == topic("unsettled") }.acknowledged)
    }

    @Test
    fun `a key that does not hash to its session topic is refused`() {
        val alphaTopic = sessions.getValue("alpha")["topic"].asString
        val wrongKey = sessions.getValue("beta")["sym"].asString
        val corrupted = ReownLegacySources(keyChain + (alphaTopic to wrongKey), sources.signDatabase, sources.coreDatabase)

        val snapshot = ReownLegacySnapshotReader().read(corrupted)

        assertTrue(snapshot.sessions.none { it.topic == alphaTopic })
    }

    @Test
    fun `pairings and the relay identity come back`() {
        val snapshot = ReownLegacySnapshotReader().read(sources)

        assertEquals(listOf("live", "old").map { pairings.getValue(it)["topic"].asString }, snapshot.pairings.map { it.topic })
        val live = snapshot.pairings.first()
        assertEquals(pairings.getValue("live")["sym"].asString, live.symKey)
        assertTrue(live.active)
        assertEquals("Alpha Swap", live.peer!!.name)
        assertEquals(reown["identitySeed"].asString, snapshot.clientSeed)
    }

    @Test
    fun `without databases only the identity is read`() {
        val snapshot = ReownLegacySnapshotReader().read(ReownLegacySources(keyChain, null, null))

        assertEquals(reown["identitySeed"].asString, snapshot.clientSeed)
        assertTrue(snapshot.sessions.isEmpty() && snapshot.pairings.isEmpty())
    }

    @Test
    fun `an unreadable identity is skipped, not guessed`() {
        val snapshot = ReownLegacySnapshotReader().read(ReownLegacySources(mapOf("key_did_keypair" to "zz"), null, null))

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
