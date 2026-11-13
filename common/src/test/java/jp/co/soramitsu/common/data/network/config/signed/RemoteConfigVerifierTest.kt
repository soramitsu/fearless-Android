package jp.co.soramitsu.common.data.network.config.signed

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class RemoteConfigVerifierTest {

    private val path = "production/chains/v13/chains.json"
    private val bundled = "bundled".toByteArray()
    private val v1 = "v1".toByteArray()
    private val v2 = "v2".toByteArray()

    private val directory = Files.createTempDirectory("remote-config-store").toFile()
    private val store = RemoteConfigStore(directory)
    private var now = 1_000_000L
    private var published: ByteArray? = null
    private var manifestFetches = 0

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private fun snapshot(manifest: ByteArray? = null) = RemoteConfigSnapshot { asset ->
        when (asset) {
            "remote-config-snapshot/index.json" -> "{\"files\":{\"$path\":\"${TestManifests.sha256(bundled)}\"}}".byteInputStream()
            "remote-config-snapshot/files/$path" -> ByteArrayInputStream(bundled)
            "remote-config-snapshot/remote-config-manifest.json" -> manifest?.let(::ByteArrayInputStream)
            else -> null
        }
    }

    private fun verifier(snapshot: RemoteConfigSnapshot = snapshot()) = RemoteConfigVerifier(
        fetchManifest = { manifestFetches++; published },
        store = store,
        snapshot = snapshot,
        verify = TestManifests::verify,
        clock = { now }
    )

    @Test
    fun `a file the signed manifest lists is used and kept`() {
        published = TestManifests.signed(mapOf(path to v1), 1)
        val resolved = verifier().resolve(path, v1)!!
        assertEquals(RemoteConfigVerifier.Source.NETWORK, resolved.source)
        assertArrayEquals(v1, resolved.content)
        assertArrayEquals(v1, store.lastVerified(path))
    }

    @Test
    fun `a changed file falls back to the last verified copy`() {
        published = TestManifests.signed(mapOf(path to v1), 1)
        val verifier = verifier()
        verifier.resolve(path, v1)
        val resolved = verifier.resolve(path, "tampered".toByteArray())!!
        assertEquals(RemoteConfigVerifier.Source.LAST_VERIFIED, resolved.source)
        assertArrayEquals(v1, resolved.content)
    }

    @Test
    fun `without a manifest or a verified copy the bundled snapshot is used`() {
        published = null
        val resolved = verifier().resolve(path, v1)!!
        assertEquals(RemoteConfigVerifier.Source.BUNDLED, resolved.source)
        assertArrayEquals(bundled, resolved.content)
    }

    @Test
    fun `a bad signature is never trusted`() {
        val manifest = TestManifests.canonical(mapOf(path to TestManifests.sha256(v1)), 1)
        published = TestManifests.envelope(manifest, ByteArray(64))
        assertEquals(RemoteConfigVerifier.Source.BUNDLED, verifier().resolve(path, v1)!!.source)
    }

    @Test
    fun `an offline fetch uses the last verified copy`() {
        published = TestManifests.signed(mapOf(path to v1), 1)
        verifier().resolve(path, v1)
        // A new process: the verified copy and manifest survive in storage.
        published = null
        val resolved = verifier().resolve(path, null)!!
        assertEquals(RemoteConfigVerifier.Source.LAST_VERIFIED, resolved.source)
        assertArrayEquals(v1, resolved.content)
    }

    @Test
    fun `an older signed manifest cannot bring back an older file`() {
        published = TestManifests.signed(mapOf(path to v2), 2)
        verifier().resolve(path, v2)
        published = TestManifests.signed(mapOf(path to v1), 1)
        val resolved = verifier().resolve(path, v1)!!
        assertEquals(RemoteConfigVerifier.Source.LAST_VERIFIED, resolved.source)
        assertArrayEquals(v2, resolved.content)
    }

    @Test
    fun `the bundled manifest sets the floor for a first start`() {
        val bundledManifest = TestManifests.signed(mapOf(path to v2), 5)
        published = TestManifests.signed(mapOf(path to v1), 4)
        val resolved = verifier(snapshot(bundledManifest)).resolve(path, v1)!!
        assertEquals(RemoteConfigVerifier.Source.BUNDLED, resolved.source)
        // The bundled manifest itself still verifies a matching fetched file.
        assertEquals(RemoteConfigVerifier.Source.NETWORK, verifier(snapshot(bundledManifest)).resolve(path, v2)!!.source)
    }

    @Test
    fun `a newer file is accepted once the newer manifest is fetched, at most every retry interval`() {
        published = TestManifests.signed(mapOf(path to v1), 1)
        val verifier = verifier()
        verifier.resolve(path, v1)
        published = TestManifests.signed(mapOf(path to v2), 2)
        now += RemoteConfigVerifier.RETRY_MILLIS + 1
        assertEquals(RemoteConfigVerifier.Source.NETWORK, verifier.resolve(path, v2)!!.source)
        val fetches = manifestFetches
        verifier.resolve(path, "tampered".toByteArray())
        verifier.resolve(path, "tampered".toByteArray())
        assertEquals(fetches, manifestFetches)
    }

    @Test
    fun `nothing is returned for a file that was never verified or bundled`() {
        published = null
        assertNull(verifier().resolve("production/xcm/v2/xcm_fees.json", "x".toByteArray()))
    }

    @Test
    fun `a damaged stored copy is not returned`() {
        store.saveVerified(path, v1)
        directory.resolve("files").listFiles()!!.single().writeBytes("damaged".toByteArray())
        assertNull(store.lastVerified(path))
    }
}
