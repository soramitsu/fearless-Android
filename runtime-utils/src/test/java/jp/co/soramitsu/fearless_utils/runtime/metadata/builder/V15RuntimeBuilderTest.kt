package jp.co.soramitsu.fearless_utils.runtime.metadata.builder

import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toByteArray
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ParityFixtures
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.fearless_utils.runtime.metadata.call
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntryType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger
import java.util.zip.GZIPInputStream

/** V15 metadata builds the runtime model V14 metadata of the same block builds, so V15 can stand in when signing. */
class V15RuntimeBuilderTest {

    private class Pair14And15(val chain: String, val types: String)

    private val chains = listOf(
        Pair14And15("polkadot", "parity/polkadot_parity_types.json"),
        Pair14And15("westend", "parity/westend_parity_types.json"),
        Pair14And15("polkadot-assethub", "parity/statemint_parity_types.json")
    )

    private fun metadataHex(resource: String): String {
        val stream = requireNotNull(javaClass.classLoader!!.getResourceAsStream(resource)) { resource }
        return GZIPInputStream(stream).use { it.readBytes() }.toHexString(withPrefix = true)
    }

    private fun runtime(chain: Pair14And15, version: Int): RuntimeSnapshot =
        ParityFixtures.buildRuntime(metadataHex("metadata-hash/${chain.chain}-v$version.scale.gz"), chain.types)

    @Test
    fun `the reader parses V15 with its type registry`() {
        val reader = RuntimeMetadataReader.read(metadataHex("metadata-hash/westend-v15.scale.gz"))
        assertEquals(15, reader.metadataVersion)
        assertNotNull(reader.lookup)
    }

    @Test
    fun `metadata versions after V15 are refused rather than read as another format`() {
        val v15 = metadataHex("metadata-hash/westend-v15.scale.gz")
        // Byte 4 after the magic is the version: 0x0f -> 0x10.
        val v16 = v15.substring(0, 10) + "10" + v15.substring(12)
        assertThrows(IllegalArgumentException::class.java) { RuntimeMetadataReader.read(v16) }
    }

    @Test
    fun `pallets, calls, storage, events and constants match the V14 model`() {
        chains.forEach { chain ->
            val v14 = runtime(chain, 14).metadata
            val v15 = runtime(chain, 15).metadata
            assertEquals(chain.chain, 15, v15.runtimeVersion.toInt())
            assertEquals(chain.chain, v14.extrinsic.version, v15.extrinsic.version)
            assertEquals(chain.chain, v14.extrinsic.signedExtensions, v15.extrinsic.signedExtensions)
            assertEquals(chain.chain, v14.extrinsic.signedExtensionTypes.keys, v15.extrinsic.signedExtensionTypes.keys)
            assertEquals(chain.chain, v14.modules.keys, v15.modules.keys)
            v14.modules.forEach { (name, module14) ->
                val module15 = v15.modules.getValue(name)
                val label = "${chain.chain} $name"
                assertEquals(label, module14.index, module15.index)
                assertEquals(label, module14.calls?.mapValues { (_, f) -> f.index to f.arguments.map { it.name } },
                    module15.calls?.mapValues { (_, f) -> f.index to f.arguments.map { it.name } })
                assertEquals(label, module14.events?.mapValues { it.value.index }, module15.events?.mapValues { it.value.index })
                assertEquals(label, module14.errors.keys, module15.errors.keys)
                assertEquals(label, module14.constants.mapValues { it.value.value.toHexString() }, module15.constants.mapValues { it.value.value.toHexString() })
                assertEquals(label, module14.storage?.prefix, module15.storage?.prefix)
                assertEquals(label, module14.storage?.entries?.mapValues { (_, entry) -> entry.shape() },
                    module15.storage?.entries?.mapValues { (_, entry) -> entry.shape() })
            }
        }
    }

    @Test
    fun `a call encodes to the same bytes with the V14 and the V15 runtime`() {
        chains.forEach { chain ->
            val encodings = listOf(14, 15).map { version ->
                val runtime = runtime(chain, version)
                val balances = runtime.metadata.module("Balances")
                val call = GenericCall.Instance(
                    balances,
                    balances.call("transfer_keep_alive"),
                    mapOf("dest" to multiAddressFromId(ByteArray(32) { 7 }), "value" to BigInteger.TEN.pow(12))
                )
                GenericCall.toByteArray(runtime, call)
            }
            assertArrayEquals(chain.chain, encodings[0], encodings[1])
        }
    }

    private fun jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry.shape(): String = when (val entryType = type) {
        is StorageEntryType.Plain -> "plain:$modifier"
        is StorageEntryType.NMap -> "map:$modifier:${entryType.hashers}:${entryType.keys.size}"
    } + ":" + default.toHexString()
}
