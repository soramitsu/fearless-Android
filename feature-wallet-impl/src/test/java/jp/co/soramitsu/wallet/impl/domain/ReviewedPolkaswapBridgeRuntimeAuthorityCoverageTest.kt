package jp.co.soramitsu.wallet.impl.domain

import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import jp.co.soramitsu.common.data.network.runtime.binding.Binder
import jp.co.soramitsu.common.data.network.runtime.binding.BinderWithKey
import jp.co.soramitsu.common.data.network.runtime.binding.BlockHash
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.rpc.RpcRuntimeIdentity
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.storage.source.ChildKeyBuilder
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.StorageKey
import jp.co.soramitsu.xcm.ReviewedBridgeRoute
import jp.co.soramitsu.xcm.ReviewedBridgeRuntimeIdentity
import jp.co.soramitsu.xcm.ReviewedBridgeRuntimeMinimum
import jp.co.soramitsu.xcm.ReviewedPolkaswapBridgeCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the SORA ParachainBridgeApp registration proofs (relay and parachain routes), the SORA burn to Liberland, and
 * every destination minimum variant. Storage answers are SCALE values encoded by hand and decoded with the recorded
 * SORA (133), Liberland (34) and Acala (2370) runtime metadata.
 */
class ReviewedPolkaswapBridgeRuntimeAuthorityCoverageTest {

    private val chainRegistry = mockk<ChainRegistry>()
    private val rpcCalls = mockk<RpcCalls>()

    @Test
    fun `resolve maps the live rpc runtime identity`() = runBlocking {
        coEvery { rpcCalls.getRuntimeIdentity(SORA) } returns RpcRuntimeIdentity("0x$SORA", "sora-substrate", 133, 1)

        val identity = authority(RecordingStorage(emptyMap())).resolve(SORA)

        assertEquals(ReviewedBridgeRuntimeIdentity("0x$SORA", "sora-substrate", 133, 1), identity)
    }

    @Test
    fun `a SORA burn to a relay chain proves the relaychain asset registration`() = runBlocking {
        val storage = RecordingStorage(relayValues(KUSAMA, KSM, "0x0c"))
        val route = retired(SORA, ReviewedPolkaswapBridgeCatalog.KUSAMA_CHAIN_ID)

        val proof = authority(storage).verify(route)

        val expected = fingerprint(
            "registration", route.routeId,
            SORA, "ParachainBridgeApp", "Kusama", KSM,
            "relaychainAsset", KSM,
            "assetKind=Sidechain", "sidechainPrecision=12"
        )
        assertEquals(expected, proof)
        assertEquals(
            listOf(relayKey(KUSAMA), kindsKey(KUSAMA, KSM), precisionKey(KUSAMA, KSM)),
            storage.readKeys
        )
    }

    @Test
    fun `relay routes in both directions share the relaychain registration`() = runBlocking {
        val storage = RecordingStorage(relayValues(POLKADOT, DOT, "0x0a"))
        val burn = retired(SORA, ReviewedPolkaswapBridgeCatalog.POLKADOT_CHAIN_ID)
        val external = retired(ReviewedPolkaswapBridgeCatalog.POLKADOT_CHAIN_ID, SORA)

        val burnProof = authority(storage).verify(burn)
        val externalProof = authority(storage).verify(external)

        val parts = arrayOf(
            SORA, "ParachainBridgeApp", "Polkadot", DOT,
            "relaychainAsset", DOT,
            "assetKind=Sidechain", "sidechainPrecision=10"
        )
        assertEquals(fingerprint("registration", burn.routeId, *parts), burnProof)
        assertEquals(fingerprint("registration", external.routeId, *parts), externalProof)
    }

    @Test
    fun `a SORA burn to a parachain proves the allowed parachain assets`() = runBlocking {
        val storage = RecordingStorage(parachainValues(listOf(OTHER, ACA)))
        val route = retired(SORA, ReviewedPolkaswapBridgeCatalog.ACALA_CHAIN_ID)

        val proof = authority(storage).verify(route)

        val expected = fingerprint(
            "registration", route.routeId,
            SORA, "ParachainBridgeApp", "Polkadot", ACA,
            "allowedParachainAssets", "2000", "$OTHER,$ACA",
            "assetKind=Sidechain", "sidechainPrecision=12"
        )
        assertEquals(expected, proof)
        assertEquals(allowedKey(POLKADOT, PARA_2000), storage.readKeys.first())
    }

    @Test
    fun `an external origin proves registration through its bridge parachain`() = runBlocking {
        val storage = RecordingStorage(parachainValues(listOf(ACA)))
        val route = retired(ReviewedPolkaswapBridgeCatalog.ACALA_CHAIN_ID, SORA)

        val proof = authority(storage).verify(route)

        val expected = fingerprint(
            "registration", route.routeId,
            SORA, "ParachainBridgeApp", "Polkadot", ACA,
            "allowedParachainAssets", "2000", ACA,
            "assetKind=Sidechain", "sidechainPrecision=12"
        )
        assertEquals(expected, proof)
    }

    @Test
    fun `parachain registration drift is refused`() {
        val acalaBurn = retired(SORA, ReviewedPolkaswapBridgeCatalog.ACALA_CHAIN_ID)
        val acalaExternal = retired(ReviewedPolkaswapBridgeCatalog.ACALA_CHAIN_ID, SORA)

        assertDrift(RecordingStorage(parachainValues(listOf(OTHER))), acalaBurn)
        assertDrift(RecordingStorage(parachainValues(listOf(OTHER))), acalaExternal)
    }

    @Test
    fun `relay registration drift is refused`() {
        val ksm = retired(SORA, ReviewedPolkaswapBridgeCatalog.KUSAMA_CHAIN_ID)

        // Another asset registered for the relay chain.
        assertDrift(RecordingStorage(relayValues(KUSAMA, KSM, "0x0c") + (relayKey(KUSAMA) to OTHER)), ksm)
        // Thischain where the route reviews a sidechain asset.
        assertDrift(RecordingStorage(relayValues(KUSAMA, KSM, "0x0c") + (kindsKey(KUSAMA, KSM) to "0x00")), ksm)
        // 18 decimals where the route reviews 12.
        assertDrift(RecordingStorage(relayValues(KUSAMA, KSM, "0x12")), ksm)
    }

    @Test
    fun `a SORA burn to Liberland reads the SubstrateBridgeApp registration`() = runBlocking {
        for (symbol in listOf("LLD", "LLM", "XOR")) {
            val storage = RecordingStorage(LIBERLAND_VALUES)
            val route = ReviewedPolkaswapBridgeCatalog.retiredRoutes.single {
                it.symbol == symbol && it.destinationChainId == ReviewedPolkaswapBridgeCatalog.LIBERLAND_CHAIN_ID
            }

            val proof = authority(storage).verify(route)

            assertTrue(symbol, proof.startsWith("0x") && proof.length == HASH_HEX_LENGTH)
            assertEquals(symbol, 3, storage.readKeys.size)
            assertTrue(symbol, storage.readKeys.all { it.startsWith(SUBSTRATE_BRIDGE_PREFIX) })
        }
    }

    @Test
    fun `a route without a destination minimum proves a zero minimum`() = runBlocking {
        val route = ReviewedPolkaswapBridgeCatalog.executableRoutes.first()

        val minimum = authority(RecordingStorage(emptyMap())).destinationMinimum(route)

        assertEquals(ReviewedBridgeRuntimeMinimum(BigInteger.ZERO, fingerprint("minimum", route.routeId, "none")), minimum)
    }

    @Test
    fun `the Balances existential deposit is converted into origin planks`() = runBlocking {
        val route = retiredToLiberland("LLD")
        coEvery { chainRegistry.getRuntime(ReviewedPolkaswapBridgeCatalog.LIBERLAND_CHAIN_ID) } returns liberlandRuntime

        val minimum = authority(RecordingStorage(emptyMap())).destinationMinimum(route)

        // Liberland keeps 1 LLD (12 decimals) as its existential deposit; SORA counts LLD with 18 decimals.
        val expectedProof = fingerprint(
            "minimum", route.routeId,
            ReviewedPolkaswapBridgeCatalog.LIBERLAND_CHAIN_ID, "Balances.ExistentialDeposit", "1000000000000"
        )
        assertEquals(ReviewedBridgeRuntimeMinimum(BigInteger.TEN.pow(18), expectedProof), minimum)
    }

    @Test
    fun `a zero existential deposit is not a usable minimum`() {
        val route = retiredToLiberland("LLD")
        // SORA's own existential deposit is zero.
        coEvery { chainRegistry.getRuntime(ReviewedPolkaswapBridgeCatalog.LIBERLAND_CHAIN_ID) } returns soraRuntime

        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { authority(RecordingStorage(emptyMap())).destinationMinimum(route) }
        }
        assertEquals("cross_chain_runtime_minimum_unavailable", failure.message)
    }

    @Test
    fun `a runtime without Balances or its existential deposit has no minimum`() {
        val route = retiredToLiberland("LLD")
        val noBalances = RuntimeSnapshot(mockk(), RuntimeMetadata(BigInteger.ONE, emptyMap(), mockk()))
        val balancesModule = Module("Balances", null, null, null, emptyMap(), emptyMap(), BigInteger.TEN)
        val noDeposit = RuntimeSnapshot(
            mockk(),
            RuntimeMetadata(BigInteger.ONE, mapOf("Balances" to balancesModule), mockk())
        )

        for (runtime in listOf(noBalances, noDeposit)) {
            coEvery { chainRegistry.getRuntime(ReviewedPolkaswapBridgeCatalog.LIBERLAND_CHAIN_ID) } returns runtime
            val failure = assertThrows(IllegalStateException::class.java) {
                runBlocking { authority(RecordingStorage(emptyMap())).destinationMinimum(route) }
            }
            assertEquals("cross_chain_runtime_minimum_unavailable", failure.message)
        }
    }

    @Test
    fun `an Assets min balance is read from the destination asset details`() = runBlocking {
        val route = retiredToLiberland("LLM")
        val key = liberlandRuntime.metadata.module("Assets").storage("Asset")
            .storageKey(liberlandRuntime, BigInteger.ONE)
        val storage = RecordingStorage(mapOf(key to llmAssetDetails()))

        val minimum = authority(storage).destinationMinimum(route)

        val expectedProof = fingerprint(
            "minimum", route.routeId,
            ReviewedPolkaswapBridgeCatalog.LIBERLAND_CHAIN_ID, "Assets.Asset", "1", "1000000"
        )
        assertEquals(ReviewedBridgeRuntimeMinimum(BigInteger.TEN.pow(12), expectedProof), minimum)
        assertEquals(listOf(key), storage.readKeys)
    }

    @Test
    fun `an Acala token minimum is read from the asset registry`() = runBlocking {
        val route = retired(SORA, ReviewedPolkaswapBridgeCatalog.ACALA_CHAIN_ID)
        val currencyId = DictEnum.Entry("NativeAssetId", DictEnum.Entry("Token", DictEnum.Entry("ACA", null)))
        val key = acalaRuntime.metadata.module("AssetRegistry").storage("AssetMetadatas")
            .storageKey(acalaRuntime, currencyId)
        val storage = RecordingStorage(mapOf(key to ACA_METADATA))

        val minimum = authority(storage).destinationMinimum(route)

        // 0.1 ACA (12 decimals on Acala) is 10^17 planks of SORA's 18-decimal ACA.
        val expectedProof = fingerprint(
            "minimum", route.routeId,
            ReviewedPolkaswapBridgeCatalog.ACALA_CHAIN_ID, "AssetRegistry.AssetMetadatas",
            "NativeAssetId.Token.ACA", "100000000000"
        )
        assertEquals(ReviewedBridgeRuntimeMinimum(BigInteger.TEN.pow(17), expectedProof), minimum)
        assertEquals(listOf(key), storage.readKeys)
    }

    @Test
    fun `a SORA parachain minimum requires the reviewed parachain genesis`() {
        val route = retired(ReviewedPolkaswapBridgeCatalog.POLKADOT_CHAIN_ID, SORA)
        coEvery { rpcCalls.getRuntimeIdentity(SORA_PARACHAIN) } returns
            RpcRuntimeIdentity("0x" + "11".repeat(32), "sora-parachain", 1, 1)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { authority(RecordingStorage(emptyMap())).destinationMinimum(route) }
        }
        assertEquals("cross_chain_runtime_genesis_mismatch", failure.message)
    }

    @Test
    fun `a SORA parachain runtime without XcmApp storage is a missing capability`() {
        val route = retired(ReviewedPolkaswapBridgeCatalog.POLKADOT_CHAIN_ID, SORA)
        coEvery { rpcCalls.getRuntimeIdentity(SORA_PARACHAIN) } returns
            RpcRuntimeIdentity("0x$SORA_PARACHAIN", "sora-parachain", 1, 1)
        // The recorded SORA mainnet runtime has no XcmApp pallet.
        val storage = RecordingStorage(emptyMap(), runtimes = mapOf(SORA_PARACHAIN to soraRuntime))

        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { authority(storage).destinationMinimum(route) }
        }
        assertEquals("cross_chain_runtime_capability_missing", failure.message)
    }

    private fun authority(storage: StorageDataSource) =
        ReviewedPolkaswapBridgeRuntimeAuthority(chainRegistry, storage, rpcCalls)

    private fun assertDrift(storage: StorageDataSource, route: ReviewedBridgeRoute) {
        val failure = runCatching { runBlocking { authority(storage).verify(route) } }.exceptionOrNull()
        assertEquals("cross_chain_runtime_registration_drift", failure?.message)
    }

    private fun retired(originChainId: String, destinationChainId: String): ReviewedBridgeRoute =
        ReviewedPolkaswapBridgeCatalog.retiredRoutes.single {
            it.originChainId == originChainId && it.destinationChainId == destinationChainId
        }

    private fun retiredToLiberland(symbol: String): ReviewedBridgeRoute =
        ReviewedPolkaswapBridgeCatalog.retiredRoutes.single {
            it.symbol == symbol && it.destinationChainId == ReviewedPolkaswapBridgeCatalog.LIBERLAND_CHAIN_ID
        }

    private fun fingerprint(kind: String, routeId: String, vararg parts: String): String =
        (listOf("1", kind, routeId) + parts).joinToString("\u001f")
            .toByteArray().blake2b256().toHexString(withPrefix = true)

    /** Answers storage reads from fixed SCALE values keyed by full storage key, decoded with the chain's runtime. */
    private class RecordingStorage(
        private val values: Map<String, String>,
        private val runtimes: Map<String, RuntimeSnapshot> = DEFAULT_RUNTIMES
    ) : StorageDataSource {
        val readKeys = mutableListOf<String>()

        override suspend fun <T> query(
            chainId: String,
            keyBuilder: (RuntimeSnapshot) -> StorageKey?,
            at: BlockHash?,
            binding: Binder<T>
        ): T {
            val runtime = runtimes.getValue(chainId)
            val key = requireNotNull(keyBuilder(runtime))
            readKeys += key
            return binding(values[key], runtime)
        }

        override suspend fun <K, T> queryKeys(
            chainId: String,
            keysBuilder: (RuntimeSnapshot) -> Map<StorageKey, K>,
            at: BlockHash?,
            binding: Binder<T>
        ): Map<K, T> = error("not used")

        override fun <T> observe(chainId: String, keyBuilder: (RuntimeSnapshot) -> StorageKey?, binder: Binder<T>): Flow<T> =
            error("not used")

        override suspend fun <K, T> queryByPrefix(
            chainId: String,
            prefixKeyBuilder: (RuntimeSnapshot) -> StorageKey?,
            keyExtractor: (String) -> K,
            binding: BinderWithKey<T, K>
        ): Map<K, T> = error("not used")

        override suspend fun <T> queryChildState(
            chainId: String,
            storageKeyBuilder: (RuntimeSnapshot) -> StorageKey,
            childKeyBuilder: ChildKeyBuilder,
            binder: Binder<T>
        ): T = error("not used")
    }

    private companion object {
        const val SORA = ReviewedPolkaswapBridgeCatalog.SORA_CHAIN_ID
        const val SORA_PARACHAIN = "e92d165ad41e41e215d09713788173aecfdbe34d3bed29409d33a2ef03980738"
        const val HASH_HEX_LENGTH = 66

        const val KSM = "0x00117b0fa73c4672e03a7d9d774e3b3f91beb893e93d9a8d0430295f44225db8"
        const val DOT = "0x0003b1dbee890acfb1b3bc12d1bb3b4295f52755423f84d1751b2545cebf000b"
        const val ACA = "0x001ddbe1a880031da72f7ea421260bec635fa7d1aa72593d5412795408b6b2ba"
        val OTHER = "0x" + "ab".repeat(32)

        // bridge_types::SubNetworkId variants and the u32 para id 2000, as SCALE.
        const val KUSAMA = "01"
        const val POLKADOT = "02"
        const val PARA_2000 = "d0070000"

        // AssetMetadata { name: "Acala", symbol: "ACA", decimals: 12, minimal_balance: 10^11 }.
        const val ACA_METADATA = "0x144163616c610c4143410c00e87648170000000000000000000000"

        // SubstrateBridgeApp storage on SORA mainnet, 2026-10-24 (see ReviewedPolkaswapBridgeRuntimeAuthorityTest).
        const val SUBSTRATE_BRIDGE_PREFIX = "0x4526e711b452bca168ee3f29b94c53c5"
        private const val SB_KINDS = "b29597888047ffc2171a755bc80a49a6"
        private const val SB_PRECISION = "ed2f588d6395bbec42db9e818fb565c6"
        private const val SB_EXTERNAL_ID = "e5afed40ca0626165fc08250676e1e95"
        private const val SB_LIBERLAND = "05"
        private const val SB_LLD = "00513be65493a7fc3e2128d4230061a530acf40478a4affa20bbba27a310673e"
        private const val SB_LLM = "00073edd278e1bd6a7f9d0b27d4f3e93b73c8f0832b58a4df13c69611a99f156"
        private const val SB_XOR = "0200000000000000000000000000000000000000000000000000000000000000"

        val LIBERLAND_VALUES = mapOf(
            SUBSTRATE_BRIDGE_PREFIX + SB_KINDS + SB_LIBERLAND + SB_LLD to "0x01",
            SUBSTRATE_BRIDGE_PREFIX + SB_KINDS + SB_LIBERLAND + SB_LLM to "0x01",
            SUBSTRATE_BRIDGE_PREFIX + SB_KINDS + SB_LIBERLAND + SB_XOR to "0x00",
            SUBSTRATE_BRIDGE_PREFIX + SB_PRECISION + SB_LIBERLAND + SB_LLD to "0x0c",
            SUBSTRATE_BRIDGE_PREFIX + SB_PRECISION + SB_LIBERLAND + SB_LLM to "0x0c",
            SUBSTRATE_BRIDGE_PREFIX + SB_PRECISION + SB_LIBERLAND + SB_XOR to "0x12",
            SUBSTRATE_BRIDGE_PREFIX + SB_EXTERNAL_ID + SB_LIBERLAND + SB_LLD to "0x0300",
            SUBSTRATE_BRIDGE_PREFIX + SB_EXTERNAL_ID + SB_LIBERLAND + SB_LLM to "0x030101000000",
            SUBSTRATE_BRIDGE_PREFIX + SB_EXTERNAL_ID + SB_LIBERLAND + SB_XOR to "0x0301150b292e"
        )

        val soraRuntime: RuntimeSnapshot by lazy { runtime("sora") }
        val liberlandRuntime: RuntimeSnapshot by lazy { runtime("liberland") }
        val acalaRuntime: RuntimeSnapshot by lazy { runtime("acala") }

        val DEFAULT_RUNTIMES: Map<String, RuntimeSnapshot> by lazy {
            mapOf(
                SORA to soraRuntime,
                ReviewedPolkaswapBridgeCatalog.LIBERLAND_CHAIN_ID to liberlandRuntime,
                ReviewedPolkaswapBridgeCatalog.ACALA_CHAIN_ID to acalaRuntime
            )
        }

        fun parachainBridgePrefix(entryName: String): String =
            soraRuntime.metadata.module("ParachainBridgeApp").storage(entryName).storageKey()

        // ParachainBridgeApp keys use the Identity hasher: the prefix followed by the SCALE-encoded arguments.
        fun relayKey(network: String) = parachainBridgePrefix("RelaychainAsset") + network

        fun kindsKey(network: String, asset: String) =
            parachainBridgePrefix("AssetKinds") + network + asset.removePrefix("0x")

        fun precisionKey(network: String, asset: String) =
            parachainBridgePrefix("SidechainPrecision") + network + asset.removePrefix("0x")

        fun allowedKey(network: String, paraId: String) =
            parachainBridgePrefix("AllowedParachainAssets") + network + paraId

        fun relayValues(network: String, asset: String, precision: String): Map<String, String> = mapOf(
            relayKey(network) to asset,
            kindsKey(network, asset) to "0x01",
            precisionKey(network, asset) to precision
        )

        fun parachainValues(allowed: List<String>): Map<String, String> {
            val length = "%02x".format(allowed.size * 4)
            return mapOf(
                allowedKey(POLKADOT, PARA_2000) to "0x" + length + allowed.joinToString("") { it.removePrefix("0x") },
                kindsKey(POLKADOT, ACA) to "0x01",
                precisionKey(POLKADOT, ACA) to "0x0c"
            )
        }

        /** pallet_assets AssetDetails with zero accounts and balances except min_balance = 10^6. */
        fun llmAssetDetails(): String = "0x" +
            "00".repeat(32 * 4) + // owner, issuer, admin, freezer
            "00".repeat(16 * 2) + // supply, deposit
            "40420f00000000000000000000000000" + // min_balance
            "00" + // is_sufficient
            "00000000".repeat(3) + // accounts, sufficients, approvals
            "00" // status: Live

        fun runtime(name: String): RuntimeSnapshot {
            val file = listOf(
                File("src/test/resources/prod-parity/runtime/$name.json.gz"),
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
