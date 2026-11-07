package jp.co.soramitsu.common.data.network.config.signed

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SignedRemoteConfigManifestTest {

    private val chains = "[{\"chainId\":\"x\"}]".toByteArray()

    @Test
    fun `a good signature yields the listed files and sequence`() {
        val manifest = TestManifests.verify(TestManifests.signed(mapOf("production/chains/v13/chains.json" to chains), 7))
        assertEquals(7, manifest.sequence)
        assertTrue(manifest.lists("production/chains/v13/chains.json", chains))
        assertFalse(manifest.lists("production/chains/v13/chains.json", chains + 1))
        assertFalse(manifest.lists("production/other.json", chains))
    }

    @Test
    fun `a bad, missing or foreign signature is refused`() {
        val manifest = TestManifests.canonical(mapOf("a.json" to TestManifests.sha256(chains)), 1)
        val badSignature = TestManifests.sign(manifest.toByteArray()).also { it[0] = (it[0].toInt() xor 1).toByte() }
        val otherKey = ByteArray(32) { 9 }
        listOf(
            TestManifests.envelope(manifest, badSignature),
            TestManifests.envelope(manifest, ByteArray(64)),
            TestManifests.envelope(manifest.replace("\"sequence\":1", "\"sequence\":2"), TestManifests.sign(manifest.toByteArray())),
            "{\"algorithm\":\"ed25519\",\"manifest\":\"$manifest\"}".toByteArray(),
            ByteArray(0),
            "not json".toByteArray()
        ).forEach { envelope ->
            assertThrows(RemoteConfigVerificationException::class.java) { TestManifests.verify(envelope) }
        }
        val good = TestManifests.envelope(manifest)
        assertThrows(RemoteConfigVerificationException::class.java) { SignedRemoteConfigManifest.verify(good, otherKey, TestManifests.keyId) }
    }

    @Test
    fun `manifests for another key, repository or with unsafe paths are refused`() {
        val digest = TestManifests.sha256(chains)
        listOf(
            TestManifests.canonical(mapOf("a.json" to digest), 1, keyId = "0000000000000000"),
            TestManifests.canonical(mapOf("a.json" to digest), 1, repository = "soramitsu/fearless-iOS"),
            TestManifests.canonical(mapOf("../config/x.json" to digest), 1),
            TestManifests.canonical(mapOf("icons/a.svg" to digest), 1),
            TestManifests.canonical(mapOf("a.json" to digest.uppercase()), 1),
            TestManifests.canonical(mapOf("a.json" to digest), 0)
        ).forEach { manifest ->
            assertThrows(manifest, RemoteConfigVerificationException::class.java) { TestManifests.verify(TestManifests.envelope(manifest)) }
        }
    }

    @Test
    fun `the published manifest verifies with the embedded key and lists the current files`() {
        val root = TestManifests.repositoryRoot()
        val envelope = File(root, "remote-config/${RemoteConfigTrust.MANIFEST_PATH}").readBytes()
        val manifest = SignedRemoteConfigManifest.verify(envelope)
        val chainsFile = File(root, "remote-config/production/chains/v13/chains.json").readBytes()
        assertTrue(manifest.lists("production/chains/v13/chains.json", chainsFile))
        val configured = JsonParser.parseString(File(root, "config/remote-config-signing-key.json").readText()).asJsonObject
        assertEquals(configured["publicKey"].asString, RemoteConfigTrust.REMOTE_CONFIG_PUBLIC_KEY_HEX)
        assertEquals(configured["keyId"].asString, RemoteConfigTrust.KEY_ID)
    }
}
