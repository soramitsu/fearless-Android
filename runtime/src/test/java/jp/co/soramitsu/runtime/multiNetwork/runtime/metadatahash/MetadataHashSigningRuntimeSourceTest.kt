package jp.co.soramitsu.runtime.multiNetwork.runtime.metadatahash

import com.google.gson.JsonParser
import jp.co.soramitsu.core.extrinsic.MetadataHashUnavailableException
import jp.co.soramitsu.core.extrinsic.SigningRuntime
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.math.BigInteger
import java.util.zip.GZIPInputStream

/** The Westend V15 fixture of runtime-utils, with the hash the merkleized-metadata crate computed for it. */
class MetadataHashSigningRuntimeSourceTest {

    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "settings.gradle").isFile }
    private val fixtures = File(root, "runtime-utils/src/test/resources/metadata-hash")
    private val westend = JsonParser.parseString(File(fixtures, "vectors.json").readText()).asJsonObject["chains"].asJsonArray
        .map { it.asJsonObject }.single { it["name"].asString == "Westend" }
    private val metadata = GZIPInputStream(File(fixtures, "westend-v15.scale.gz").inputStream()).use { it.readBytes() }
    private val extensions = westend["signedExtensions"].asJsonArray.map { it.asString }
    private val version = RuntimeVersion(westend["specVersion"].asInt, westend["transactionVersion"].asInt)
    private val chain = mock<IChain> { on { id } doReturn westend["chainId"].asString }

    private fun runtime(extensions: List<String>) = RuntimeSnapshot(
        TypeRegistry(v13Preset(), DynamicTypeResolver.defaultCompoundResolver()),
        RuntimeMetadata(BigInteger.valueOf(15), emptyMap(), ExtrinsicMetadata(BigInteger.valueOf(4), extensions))
    )

    private val current = runtime(extensions)
    private val signingRuntime = runtime(extensions)

    private inner class Remote(
        var metadataV15: () -> ByteArray? = { metadata },
        var properties: Map<*, *> = mapOf("tokenDecimals" to 12.0, "tokenSymbol" to "WND", "ss58Format" to 42.0)
    ) : SigningMetadataRemote {
        var fetches = 0
        override suspend fun metadataV15(chainId: String): ByteArray? {
            fetches++
            return metadataV15.invoke()
        }
        override suspend fun systemProperties(chainId: String) = properties
    }

    private fun source(remote: SigningMetadataRemote, runtimeNow: RuntimeSnapshot = current, built: RuntimeSnapshot = signingRuntime) =
        MetadataHashSigningRuntimeSource(
            currentRuntime = { runtimeNow },
            remote = remote,
            buildRuntime = { _, bytes, specVersion ->
                assertArrayEquals(metadata, bytes)
                assertEquals(version.specVersion, specVersion)
                built
            },
            exemptions = MetadataHashExemptions(emptyMap())
        )

    @Test
    fun `a network that checks the hash signs with the V15 runtime and the RFC 78 hash`() = runBlocking {
        val signing = source(Remote()).signingRuntime(chain, version)
        signing as SigningRuntime.WithMetadataHash
        assertSame(signingRuntime, signing.runtime)
        assertEquals(westend["metadataHash"].asString, signing.metadataHash.toHexString(withPrefix = true))
    }

    @Test
    fun `the hash is computed once per runtime version`() = runBlocking {
        val remote = Remote()
        val source = source(remote)
        source.signingRuntime(chain, version)
        source.signingRuntime(chain, version)
        assertEquals(1, remote.fetches)
        assertThrows(MetadataHashUnavailableException::class.java) {
            runBlocking { source.signingRuntime(chain, RuntimeVersion(version.specVersion + 1, version.transactionVersion)) }
        }
        assertEquals(2, remote.fetches)
    }

    @Test
    fun `a network without the extension signs as before and fetches nothing`() = runBlocking {
        val remote = Remote()
        val withoutExtension = runtime(extensions - "CheckMetadataHash")
        val signing = source(remote, runtimeNow = withoutExtension).signingRuntime(chain, version)
        assertTrue(signing is SigningRuntime.WithoutMetadataCheck)
        assertSame(withoutExtension, signing.runtime)
        assertEquals(0, remote.fetches)
    }

    @Test
    fun `a reviewed exemption signs Mode Disabled without fetching`() = runBlocking {
        val remote = Remote()
        val source = MetadataHashSigningRuntimeSource({ current }, remote, { _, _, _ -> error("not built") }, MetadataHashExemptions(mapOf(chain.id to "reviewed")))
        val signing = source.signingRuntime(chain, version)
        assertTrue(signing is SigningRuntime.MetadataHashExempt)
        assertEquals(0, remote.fetches)
    }

    @Test
    fun `every way the hash cannot be computed refuses to sign`() {
        val failures = listOf<Pair<String, () -> MetadataHashSigningRuntimeSource>>(
            "no V15" to { source(Remote(metadataV15 = { null })) },
            "node error" to { source(Remote(metadataV15 = { throw IllegalStateException("rpc") })) },
            "garbage" to { source(Remote(metadataV15 = { byteArrayOf(1, 2, 3) })) },
            "no token" to { source(Remote(properties = mapOf("ss58Format" to 42.0))) },
            "other runtime" to { source(Remote(), built = runtime(extensions - "CheckMetadataHash")) }
        )
        failures.forEach { (label, source) ->
            assertThrows(label, MetadataHashUnavailableException::class.java) { runBlocking { source().signingRuntime(chain, version) } }
        }
        assertThrows(MetadataHashUnavailableException::class.java) {
            runBlocking { source(Remote()).signingRuntime(chain, RuntimeVersion(version.specVersion, version.transactionVersion + 1)) }
        }
    }
}
