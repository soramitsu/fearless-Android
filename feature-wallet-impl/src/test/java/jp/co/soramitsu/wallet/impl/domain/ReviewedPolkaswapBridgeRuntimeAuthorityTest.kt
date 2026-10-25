package jp.co.soramitsu.wallet.impl.domain

import com.google.gson.JsonParser
import io.mockk.mockk
import java.io.File
import java.util.zip.GZIPInputStream
import jp.co.soramitsu.common.data.network.runtime.binding.BlockHash
import jp.co.soramitsu.common.data.network.runtime.binding.Binder
import jp.co.soramitsu.common.data.network.runtime.binding.BinderWithKey
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.storage.source.ChildKeyBuilder
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.StorageKey
import jp.co.soramitsu.xcm.ReviewedBridgeRoute
import jp.co.soramitsu.xcm.ReviewedPolkaswapBridgeCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Liberland to SORA registration is read from SORA's SubstrateBridgeApp storage. The keys and values below were
 * read from SORA mainnet (runtime 134) on 2026-10-24 and are decoded with SORA's runtime metadata (133, the same
 * storage types).
 */
class ReviewedPolkaswapBridgeRuntimeAuthorityTest {

    private val storage = LiveStorage(LIVE_VALUES)
    private val authority = ReviewedPolkaswapBridgeRuntimeAuthority(mockk(), storage, mockk())

    @Test
    fun `LLD, LLM and XOR verify against SORA's live registration`() = runBlocking {
        for (symbol in listOf("LLD", "LLM", "XOR")) {
            assertTrue(symbol, authority.verify(route(symbol)).startsWith("0x"))
        }
        // The storage keys built from the reviewed routes are the ones that hold SORA's records.
        assertEquals(LIVE_VALUES.keys, storage.readKeys)
    }

    @Test
    fun `an external id outside the Liberland variant is drift`() {
        // GenericAssetId::Sora(0x00...00) instead of GenericAssetId::Liberland(LiberlandAssetId::LLD).
        val sora = LiveStorage(LIVE_VALUES + (LLD_ASSET_ID_KEY to "0x00" + "00".repeat(32)))

        assertDrift(sora, "LLD")
    }

    @Test
    fun `another Liberland asset is drift`() {
        // Liberland(Asset(2)) where LLM is Liberland(Asset(1)).
        val otherAsset = LiveStorage(LIVE_VALUES + (LLM_ASSET_ID_KEY to "0x030102000000"))

        assertDrift(otherAsset, "LLM")
    }

    @Test
    fun `a changed asset kind or precision is drift`() {
        assertDrift(LiveStorage(LIVE_VALUES + (XOR_KIND_KEY to "0x01")), "XOR")
        assertDrift(LiveStorage(LIVE_VALUES + (LLD_PRECISION_KEY to "0x12")), "LLD")
    }

    @Test
    fun `a missing registration is not a drift-free answer`() {
        val missing = LiveStorage(LIVE_VALUES - LLM_ASSET_ID_KEY)

        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { ReviewedPolkaswapBridgeRuntimeAuthority(mockk(), missing, mockk()).verify(route("LLM")) }
        }
        assertEquals("cross_chain_runtime_registration_missing", failure.message)
    }

    private fun assertDrift(storage: StorageDataSource, symbol: String) {
        val failure = runCatching {
            runBlocking { ReviewedPolkaswapBridgeRuntimeAuthority(mockk(), storage, mockk()).verify(route(symbol)) }
        }.exceptionOrNull()
        assertEquals("cross_chain_runtime_registration_drift", failure?.message)
    }

    private fun route(symbol: String): ReviewedBridgeRoute =
        ReviewedPolkaswapBridgeCatalog.executableRoutes.single { it.symbol == symbol }

    /** Answers storage reads from fixed live values, decoded with SORA's metadata. */
    private class LiveStorage(private val values: Map<String, String>) : StorageDataSource {
        val readKeys = mutableSetOf<String>()

        override suspend fun <T> query(
            chainId: String,
            keyBuilder: (RuntimeSnapshot) -> StorageKey?,
            at: BlockHash?,
            binding: Binder<T>
        ): T {
            assertEquals(ReviewedPolkaswapBridgeCatalog.SORA_CHAIN_ID, chainId)
            val key = requireNotNull(keyBuilder(soraRuntime))
            readKeys += key
            return binding(values[key], soraRuntime)
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
        const val PREFIX = "0x4526e711b452bca168ee3f29b94c53c5"
        const val KINDS = "b29597888047ffc2171a755bc80a49a6"
        const val PRECISION = "ed2f588d6395bbec42db9e818fb565c6"
        const val EXTERNAL_ID = "e5afed40ca0626165fc08250676e1e95"
        const val LIBERLAND = "05"
        const val LLD = "00513be65493a7fc3e2128d4230061a530acf40478a4affa20bbba27a310673e"
        const val LLM = "00073edd278e1bd6a7f9d0b27d4f3e93b73c8f0832b58a4df13c69611a99f156"
        const val XOR = "0200000000000000000000000000000000000000000000000000000000000000"

        const val LLD_PRECISION_KEY = PREFIX + PRECISION + LIBERLAND + LLD
        const val LLD_ASSET_ID_KEY = PREFIX + EXTERNAL_ID + LIBERLAND + LLD
        const val LLM_ASSET_ID_KEY = PREFIX + EXTERNAL_ID + LIBERLAND + LLM
        const val XOR_KIND_KEY = PREFIX + KINDS + LIBERLAND + XOR

        // SubstrateBridgeApp storage on SORA mainnet, 2026-10-24.
        val LIVE_VALUES = mapOf(
            PREFIX + KINDS + LIBERLAND + LLD to "0x01", // Sidechain
            PREFIX + KINDS + LIBERLAND + LLM to "0x01", // Sidechain
            XOR_KIND_KEY to "0x00", // Thischain
            LLD_PRECISION_KEY to "0x0c",
            PREFIX + PRECISION + LIBERLAND + LLM to "0x0c",
            PREFIX + PRECISION + LIBERLAND + XOR to "0x12",
            LLD_ASSET_ID_KEY to "0x0300", // Liberland(LLD)
            LLM_ASSET_ID_KEY to "0x030101000000", // Liberland(Asset(1))
            PREFIX + EXTERNAL_ID + LIBERLAND + XOR to "0x0301150b292e" // Liberland(Asset(774441749))
        )

        val soraRuntime: RuntimeSnapshot by lazy {
            val file = listOf(
                File("src/test/resources/prod-parity/runtime/sora.json.gz"),
                File("feature-wallet-impl/src/test/resources/prod-parity/runtime/sora.json.gz")
            ).first(File::isFile)
            val snapshot = GZIPInputStream(file.inputStream()).use { String(it.readBytes()) }
                .let { JsonParser.parseString(it).asJsonObject }
            RuntimeFactory(JsonFactory.create()).constructRuntime(
                metadataRaw = snapshot.get("metadata").asString,
                ownTypesRaw = snapshot.get("types").asString,
                runtimeVersion = snapshot.get("specVersion").asInt
            ).runtime
        }
    }
}
