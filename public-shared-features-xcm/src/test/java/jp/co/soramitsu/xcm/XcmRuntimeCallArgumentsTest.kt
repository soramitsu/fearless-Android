package jp.co.soramitsu.xcm

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.FixedArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u32
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class XcmRuntimeCallArgumentsTest {

    @Test
    fun `json-like v3 call is converted to runtime-native SCALE instances`() {
        val versionedLocation = ReviewedBridgeRuntimeMetadataContractFixture.versionedLocationType()
        val function = MetadataFunction(
            name = "reserveTransferAssets",
            arguments = listOf(
                FunctionArgument("dest", versionedLocation),
                FunctionArgument("fee_asset_item", u32)
            ),
            documentation = emptyList(),
            index = 1 to 2
        )
        val accountId = "0x" + "11".repeat(32)
        val call = XcmExtrinsicCall(
            moduleName = "XcmPallet",
            callName = function.name,
            arguments = linkedMapOf(
                "dest" to mapOf(
                    "V3" to mapOf(
                        "parents" to 0,
                        "interior" to mapOf(
                            "X1" to mapOf("AccountId32" to mapOf("id" to accountId))
                        )
                    )
                ),
                "feeAssetItem" to 0
            )
        )

        val runtime = call.toRuntimeArguments(function)

        assertEquals(BigInteger.ZERO, runtime["fee_asset_item"])
        assertTrue(versionedLocation.isValidInstance(runtime["dest"]))
        val versioned = runtime["dest"] as DictEnum.Entry<*>
        val location = versioned.value as Struct.Instance
        assertEquals(BigInteger.ZERO, location.mapping["parents"])
        val x1 = location.mapping["interior"] as DictEnum.Entry<*>
        val junction = (x1.value as List<*>).single() as DictEnum.Entry<*>
        val account = junction.value as Struct.Instance
        assertNull(account.mapping["network"])
        assertArrayEquals(ByteArray(32) { 0x11 }, account.mapping["id"] as ByteArray)
    }

    @Test
    fun `metadata conversion rejects unknown and ambiguous call keys`() {
        val function = MetadataFunction(
            name = "transfer",
            arguments = listOf(FunctionArgument("fee_asset_item", u32)),
            documentation = emptyList(),
            index = 1 to 2
        )

        assertThrows(IllegalArgumentException::class.java) {
            XcmExtrinsicCall("XcmPallet", "transfer", mapOf("feeAssetItem" to 0, "extra" to 1))
                .toRuntimeArguments(function)
        }
        assertThrows(IllegalArgumentException::class.java) {
            XcmExtrinsicCall(
                "XcmPallet",
                "transfer",
                mapOf("fee_asset_item" to 0, "feeAssetItem" to 0)
            ).toRuntimeArguments(function)
        }
    }

    @Test
    fun `newtype structs and tuple-wrapped vectors retain their logical payload`() {
        val assetId = Struct(
            "AssetId32",
            linkedMapOf(
                "code" to TypeReference(FixedArray("AssetCode", 32, TypeReference(u8)))
            )
        )
        val assets = Tuple(
            "Assets",
            listOf(TypeReference(Vec("Vec<AssetId32>", TypeReference(assetId))))
        )
        val logicalId = "0x" + "ab".repeat(32)

        val runtimeId = assetId.toXcmRuntimeValue(logicalId) as Struct.Instance
        val runtimeAssets = assets.toXcmRuntimeValue(listOf(logicalId)) as List<*>

        assertTrue(assetId.isValidInstance(runtimeId))
        assertEquals(BigInteger.valueOf(171), (runtimeId.mapping["code"] as List<*>).first())
        assertTrue(assets.isValidInstance(runtimeAssets))
        assertEquals(1, (runtimeAssets.single() as List<*>).size)
    }

}
