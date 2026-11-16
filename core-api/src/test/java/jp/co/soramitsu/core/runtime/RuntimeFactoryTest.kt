package jp.co.soramitsu.core.runtime

import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeFactoryTest {
    private val factory = RuntimeFactory(Json)
    private val minimalV13 = "0x6d6574610d000400"

    @Test
    fun legacyMetadataBuildsARegistryAndPreservesConstantOverridesAndHashes() {
        val own = """{"types":{"Balance":"u128"},"overrides":[{"module":"Balances","constants":[{"name":"ExistentialDeposit","value":"0x01"}]}]}"""
        val result = factory.constructRuntime(minimalV13, own, 1)
        assertEquals(BigInteger.valueOf(13), result.runtime.metadata.runtimeVersion)
        assertEquals(BigInteger.valueOf(4), result.runtime.metadata.extrinsic.version)
        assertTrue(result.runtime.metadata.modules.isEmpty())
        assertNotNull(result.runtime.typeRegistry["Balance"])
        assertEquals(mapOf("Balances" to mapOf("ExistentialDeposit" to "0x01")), result.runtime.overrides)
        assertEquals(MessageDigest.getInstance("MD5").digest(minimalV13.toByteArray()).decodeToString(), result.metadataHash)
        assertEquals(MessageDigest.getInstance("MD5").digest(own.toByteArray()).decodeToString(), result.ownTypesHash)
    }

    @Test
    fun legacyDefaultsAndMatchingVersionRangesPopulateTheRegistry() {
        val own = """{"types":{},"versioning":[{"runtime_range":[0,9],"types":{"Early":"u32"}},{"runtime_range":[10,null],"types":{"Late":"u64"}}],"overrides":[]}"""
        val defaults = """{"types":{"DefaultBalance":"u128"}}"""
        val early = factory.constructRuntimeV13(minimalV13, own, defaults, 3).runtime
        val late = factory.constructRuntimeV13(minimalV13, own, defaults, 12).runtime
        assertNotNull(early.typeRegistry["DefaultBalance"])
        assertNotNull(early.typeRegistry["Early"])
        assertNull(early.typeRegistry["Late"])
        assertNotNull(late.typeRegistry["Late"])
        assertNull(late.typeRegistry["Early"])
        assertNull(early.overrides)
    }

    @Test
    fun explicitRuntimeIdTakesPrecedenceOverTheRequestedVersion() {
        val own = """{"runtime_id":3,"types":{},"versioning":[{"runtime_range":[0,9],"types":{"Chosen":"u32"}}]}"""
        val result = factory.constructRuntime(minimalV13, own, 100)
        assertNotNull(result.runtime.typeRegistry["Chosen"])
        assertNull(result.runtime.overrides)
    }

    @Test
    fun portableMetadataBuildsRealPalletsAndSignedExtensionsFromTheRepositoryFixture() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "runtime-utils/src/test/resources/polkadot_metadata_v14").isFile }
        val metadata = File(root, "runtime-utils/src/test/resources/polkadot_metadata_v14").readText().trim()
        val result = factory.constructRuntime(metadata, """{"types":{}}""", 1)
        assertEquals(BigInteger.valueOf(14), result.runtime.metadata.runtimeVersion)
        assertTrue(result.runtime.metadata.modules.containsKey("System"))
        assertTrue(result.runtime.metadata.modules.containsKey("Balances"))
        assertTrue(result.runtime.metadata.extrinsic.signedExtensions.isNotEmpty())
        assertTrue(result.runtime.typeRegistry.types.isNotEmpty())
        assertNull(result.runtime.overrides)
    }

    @Test
    fun malformedMetadataAndTypeTreesAreRejected() {
        assertThrows(Exception::class.java) { factory.constructRuntime("0x00", """{"types":{}}""", 1) }
        assertThrows(Exception::class.java) { factory.constructRuntime(minimalV13, "not-json", 1) }
    }
}
