package jp.co.soramitsu.polkaswap.impl.data.network.blockchain.bindings

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v14Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.FixedArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageEntryModifier
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

class DexInfosBindingTest {
    private val assetId = Struct("AssetId32", linkedMapOf("code" to TypeReference(FixedArray("[u8; 32]", 32, TypeReference(u8)))))

    private fun runtime(dexInfo: Type<*>): RuntimeSnapshot {
        val entry = StorageEntry("DEXInfos", StorageEntryModifier.Optional, StorageEntryType.Plain(dexInfo), ByteArray(0), emptyList(), "DEXManager")
        val module = Module("DEXManager", Storage("DEXManager", mapOf("DEXInfos" to entry)), null, null, emptyMap(), emptyMap(), BigInteger.ONE)
        val metadata = mockk<RuntimeMetadata> { every { modules } returns mapOf("DEXManager" to module) }
        return RuntimeSnapshot(TypeRegistry(v14Preset(), DynamicTypeResolver.defaultCompoundResolver()), metadata)
    }

    @Suppress("UNCHECKED_CAST")
    private fun Type<*>.encode(runtime: RuntimeSnapshot, value: Any?) = (this as Type<Any?>).toHex(runtime, value)

    @Test fun dexBaseAssetIsReadAsItsCurrencyId() {
        val dexInfo = Struct("DEXInfo", linkedMapOf("baseAssetId" to TypeReference(assetId), "isPublic" to TypeReference(u8)))
        val runtime = runtime(dexInfo)
        val xorCode = listOf(2, 0) + List(30) { 0 }
        val scale = dexInfo.encode(
            runtime,
            Struct.Instance(mapOf("baseAssetId" to Struct.Instance(mapOf("code" to xorCode.map(Int::toBigInteger))), "isPublic" to BigInteger.ONE))
        )

        assertEquals("0x02" + "00".repeat(31), bindDexInfos(scale, runtime))
    }

    @Test fun dexInfoWithoutABaseAssetIsIncompatible() {
        val dexInfo = Struct("DEXInfo", linkedMapOf("isPublic" to TypeReference(u8)))
        val runtime = runtime(dexInfo)
        val scale = dexInfo.encode(runtime, Struct.Instance(mapOf("isPublic" to BigInteger.ONE)))

        val error = assertThrows(IllegalStateException::class.java) { bindDexInfos(scale, runtime) }
        assertEquals("Can't bind DEXManager.DEXInfos", error.message)
    }
}
