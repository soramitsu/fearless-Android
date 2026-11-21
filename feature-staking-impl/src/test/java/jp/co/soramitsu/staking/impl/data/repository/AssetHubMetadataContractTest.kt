package jp.co.soramitsu.staking.impl.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.models.MultiAddress
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v14Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.v14.TypesParserV14
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.fearless_utils.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.call
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.runtime.assethub.AssetHubRuntimeTypes
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindActiveEra
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindBondedEraSession
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindStakingLedger
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.bond
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.payoutStakers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigInteger
import java.util.zip.GZIPInputStream

@RunWith(Parameterized::class)
class AssetHubMetadataContractTest(private val network: String) {
    private val runtime get() = runtimes.getValue(network)
    private val vectors: JsonObject = javaClass.getResourceAsStream("/asset-hub/$network-vectors.json")!!.reader().use {
        Gson().fromJson(it, JsonObject::class.java)
    }
    private fun vector(name: String) = vectors[name].asString

    @Test fun `actual hub metadata has async staking without a Babe clock`() {
        assertTrue(runtime.metadata.moduleOrNull("StakingRcClient") != null)
        assertNull(runtime.metadata.moduleOrNull("Babe"))
        assertEquals(listOf("value", "payee"), runtime.metadata.staking().call("bond").arguments.map { it.name })
    }

    @Test fun `modern staking ledger has no legacy claimed reward field`() {
        val ledger = bindStakingLedger(vector("ledger"), runtime)
        assertArrayEquals(ByteArray(32) { 0x11 }, ledger.stashId)
        assertEquals(12_345.toBigInteger(), ledger.total)
        assertEquals(12_000.toBigInteger(), ledger.active)
        assertEquals(345.toBigInteger(), ledger.unlocking.single().amount)
        assertEquals(2320.toBigInteger(), ledger.unlocking.single().era)
        assertTrue(ledger.claimedRewards.isEmpty())
    }

    @Test fun `hub bonded eras select relay session for the active era`() {
        assertEquals(2310.toBigInteger(), bindActiveEra(vector("activeEra"), runtime))
        assertEquals(13_859.toBigInteger(), bindBondedEraSession(vector("bondedEras"), runtime, 2310.toBigInteger()))
        assertTrue(runCatching { bindBondedEraSession(vector("bondedEras"), runtime, 2311.toBigInteger()) }.isFailure)
    }

    @Test fun `modern bond SCALE matches independently generated runtime vector`() {
        val builder = mock<ExtrinsicBuilder>()
        whenever(builder.runtime).thenReturn(runtime)
        var encoded: String? = null
        whenever(builder.call(any<String>(), any(), any())).thenAnswer { call ->
            val module = runtime.metadata.staking()
            val name = call.getArgument<String>(1)
            val arguments = call.getArgument<Map<String, Any?>>(2)
            assertEquals(setOf("value", "payee"), arguments.keys)
            encoded = GenericCall.toHex(runtime, GenericCall.Instance(module, module.call(name), arguments))
            builder
        }
        builder.bond(MultiAddress.Id(ByteArray(32) { 17 }), 12_345.toBigInteger(), RewardDestination.Restake)
        assertEquals(vector("bondCall"), encoded)
    }

    @Test fun `payout page is encoded in both call identity and arguments`() {
        val builder = mock<ExtrinsicBuilder>()
        whenever(builder.runtime).thenReturn(runtime)
        var method: String? = null
        var page: BigInteger? = null
        whenever(builder.call(any<String>(), any(), any())).thenAnswer { call ->
            method = call.getArgument(1)
            page = call.getArgument<Map<String, Any?>>(2)["page"] as? BigInteger
            builder
        }
        builder.payoutStakers(2310.toBigInteger(), ByteArray(32), 256.toBigInteger())
        assertEquals("payout_stakers_by_page", method)
        assertEquals(256.toBigInteger(), page)
    }

    @Test fun `paged storage identity matches metadata-derived SCALE keys`() {
        val validator = ByteArray(32) { 17 }
        val key = runtime.metadata.staking().storage("ErasStakersPaged").storageKey(runtime, 2310.toBigInteger(), validator, 256.toBigInteger())
        val parsed = assetHubExposureKey(key)
        assertEquals(2310.toBigInteger(), parsed.era)
        assertArrayEquals(validator, parsed.validator)
        assertEquals(256.toBigInteger(), parsed.page)
    }

    companion object {
        internal val runtimes by lazy {
            networks().associateWith { network ->
                val data = GZIPInputStream(AssetHubMetadataContractTest::class.java.getResourceAsStream("/asset-hub/$network-metadata.scale.gz")!!).use {
                    it.readBytes()
                }
                val raw = RuntimeMetadataReader.read("0x" + data.joinToString("") { "%02x".format(it) })
                val parsed = TypesParserV14.parse(raw.metadata[RuntimeMetadataSchemaV14.lookup], v14Preset())
                val original = AssetHubMetadataContractTest::class.java.getResourceAsStream("/asset-hub/$network-types.json")?.reader()?.use {
                    it.readText()
                }
                val chainId = when (network) {
                    "polkadot" -> AssetHubStaking.POLKADOT
                    "kusama" -> AssetHubStaking.KUSAMA
                    else -> AssetHubStaking.WESTEND
                }
                val json = AssetHubRuntimeTypes.forChain(chainId, original)
                val tree = Gson().fromJson(json, TypeDefinitionsTree::class.java)
                val preset = TypeDefinitionParser.parseNetworkVersioning(tree, parsed.typePreset, currentRuntimeVersion = 2_005_001).typePreset
                val registry = TypeRegistry(preset, DynamicTypeResolver.defaultCompoundResolver())
                RuntimeSnapshot(registry, VersionedRuntimeBuilder.buildMetadata(raw, registry))
            }
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun networks() = listOf("polkadot", "kusama", "westend")
    }
}
