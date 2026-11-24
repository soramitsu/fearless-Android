package jp.co.soramitsu.common.data.network.config.signed

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteConfigSnapshotTest {

    private val path = "production/polkaswapSettings.json"
    private val content = "{}".toByteArray()

    private fun snapshot(stored: ByteArray) = RemoteConfigSnapshot { asset ->
        when (asset) {
            "remote-config-snapshot/index.json" -> "{\"files\":{\"$path\":\"${TestManifests.sha256(content)}\"}}".byteInputStream()
            "remote-config-snapshot/files/$path" -> stored.inputStream()
            "remote-config-snapshot/remote-config-manifest.json" -> "manifest".byteInputStream()
            else -> null
        }
    }

    @Test
    fun `a bundled file is returned only when it matches its pinned hash`() {
        assertArrayEquals(content, snapshot(content).read(path))
        assertNull(snapshot("changed".toByteArray()).read(path))
        assertNull(snapshot(content).read("production/other.json"))
    }

    @Test
    fun `the bundled manifest is readable and a missing snapshot reads as nothing`() {
        assertArrayEquals("manifest".toByteArray(), snapshot(content).manifestEnvelope())
        val empty = RemoteConfigSnapshot { null }
        assertNull(empty.read(path))
        assertNull(empty.manifestEnvelope())
    }
}
