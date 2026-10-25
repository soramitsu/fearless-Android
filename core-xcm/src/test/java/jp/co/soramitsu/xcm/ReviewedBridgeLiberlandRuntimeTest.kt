package jp.co.soramitsu.xcm

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toByteArray
import jp.co.soramitsu.fearless_utils.runtime.metadata.call
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File
import java.math.BigInteger
import java.util.zip.GZIPInputStream

/**
 * Resolves the executable Liberland to SORA burn against the real Liberland mainnet runtime (spec 34, read on
 * 2026-10-24): the pinned pallet and call index must be what that runtime maps `SoraBridgeApp.burn` to.
 */
class ReviewedBridgeLiberlandRuntimeTest {

    private val liberland = runtime("liberland")
    private val route = ReviewedPolkaswapBridgeCatalog.executableRoutes.single { it.symbol == "LLD" }

    @Test
    fun `the pinned burn index is the one the live Liberland runtime uses`() = runBlocking<Unit> {
        val resolution = resolver().resolve(route)

        assertEquals("SoraBridgeApp", resolution.moduleName)
        assertEquals("burn", resolution.callName)
        assertEquals(81 to 3, liberland.metadata.module("SoraBridgeApp").call("burn").index)
        assertEquals(liberland.metadata.module("SoraBridgeApp").call("burn").index, route.execution.runtimeCall.index)
    }

    @Test
    fun `every executable burn encodes with the live Liberland call types`() = runBlocking<Unit> {
        val recipient = ByteArray(32) { 7 }.toAddress(SORA_ADDRESS_PREFIX)
        val module = liberland.metadata.module("SoraBridgeApp")
        val function = module.call("burn")

        for (executable in ReviewedPolkaswapBridgeCatalog.executableRoutes) {
            val resolution = resolver().resolve(executable)
            val call = executor().buildCall(executable, resolution, recipient, BigInteger.TEN)

            val encoded = GenericCall.toByteArray(liberland, GenericCall.Instance(module, function, call.toRuntimeArguments(function)))

            // The call bytes start with the pinned pallet and call index.
            assertEquals(executable.symbol, listOf(81, 3), encoded.take(2).map { it.toInt() and 0xff })
        }
    }

    @Test
    fun `a route pinned to another call index is refused`() {
        val pinnedElsewhere = route.copy(
            execution = route.execution.copy(runtimeCall = route.execution.runtimeCall.copy(callIndex = 4))
        )

        val failure = assertThrows(IllegalArgumentException::class.java) { runBlocking { resolver().resolve(pinnedElsewhere) } }

        assertEquals("cross_chain_runtime_execution_drift", failure.message)
    }

    private fun executor() = ReviewedPolkaswapBridgeExecutor(
        runtimeResolver = { error("not used") },
        balanceReader = { _, _, _, _ -> error("not used") },
        submitter = object : XcmExtrinsicSubmitter {
            override suspend fun submit(
                chain: Chain,
                accountId: ByteArray,
                keypairProvider: jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider,
                call: XcmExtrinsicCall
            ): String = error("not used")

            override suspend fun estimateFee(
                chain: Chain,
                accountId: ByteArray,
                keypairProvider: jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider,
                call: XcmExtrinsicCall
            ): BigInteger = error("not used")
        },
        mutationsEnabled = { false }
    )

    private fun resolver(): ChainRegistryReviewedBridgeRuntimeResolver {
        val chainRegistry = mock(ChainRegistry::class.java)
        runBlocking {
            `when`(chainRegistry.getChain(route.originChainId)).thenReturn(chain(route.originChainId))
            `when`(chainRegistry.getChain(route.destinationChainId)).thenReturn(chain(route.destinationChainId))
            `when`(chainRegistry.getRuntime(route.originChainId)).thenReturn(liberland)
        }
        return ChainRegistryReviewedBridgeRuntimeResolver(
            chainRegistry = chainRegistry,
            runtimeIdentityResolver = { chainId -> ReviewedBridgeRuntimeIdentity("0x$chainId", "fixture", 1, 1) },
            registrationAuthority = { "registration" },
            runtimeMinimumResolver = { ReviewedBridgeRuntimeMinimum(BigInteger.ZERO, "none") }
        )
    }

    private companion object {
        const val SORA_ADDRESS_PREFIX: Short = 69

        val bundledChains: List<Chain> by lazy {
            val file = listOf(File("../runtime/src/main/assets/local_chains.json"), File("runtime/src/main/assets/local_chains.json"))
                .first(File::isFile)
            val type = object : TypeToken<List<ChainRemote>>() {}.type
            Gson().fromJson<List<ChainRemote>>(file.readText(), type).map(ChainRemote::toChain)
        }

        fun chain(id: String): Chain = bundledChains.single { it.id == id }

        fun runtime(name: String): RuntimeSnapshot {
            val file = listOf(
                File("../feature-wallet-impl/src/test/resources/prod-parity/runtime/$name.json.gz"),
                File("feature-wallet-impl/src/test/resources/prod-parity/runtime/$name.json.gz")
            ).first(File::isFile)
            val snapshot = GZIPInputStream(file.inputStream()).use { String(it.readBytes()) }
                .let { JsonParser.parseString(it).asJsonObject }
            return RuntimeFactory(JsonFactory.create()).constructRuntime(
                metadataRaw = snapshot.get("metadata").asString,
                ownTypesRaw = snapshot.get("types").asString,
                runtimeVersion = snapshot.get("specVersion").asInt
            ).runtime
        }
    }
}
