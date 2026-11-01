package jp.co.soramitsu.runtime.assethub

import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTreeV2
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetHubRuntimeTypesTest {
    @Test fun `Hub signing requires identity mortality nonce metadata and native fee extensions`() {
        val valid = listOf("CheckSpecVersion", "CheckTxVersion", "CheckGenesis", "CheckMortality", "CheckNonce", "CheckMetadataHash", "ChargeAssetTxPayment")
        AssetHubRuntimeTypes.requireSigningExtensions(AssetHubStaking.POLKADOT, valid)
        for (extension in valid) {
            assertTrue(
                runCatching {
                AssetHubRuntimeTypes.requireSigningExtensions(AssetHubStaking.POLKADOT, valid - extension)
            }.isFailure
            )
        }
        assertTrue(
            runCatching {
            AssetHubRuntimeTypes.requireSigningExtensions(AssetHubStaking.POLKADOT, valid + "CheckNonce")
        }.isFailure
        )
        AssetHubRuntimeTypes.requireSigningExtensions("other", emptyList())
    }

    @Test fun `Westend bootstrap supplies missing native aliases and current runtime call namespace`() {
        val result = AssetHubRuntimeTypes.forChain(AssetHubStaking.WESTEND, null)!!
        val types = Json.parseToJsonElement(result).jsonObject.getValue("versioning").jsonArray.single().jsonObject.getValue("types").jsonObject
        assertEquals("GenericCall", types.getValue("asset_hub_westend_runtime::RuntimeCall").jsonPrimitive.content)
        assertEquals("GenericAccountId", types.getValue("sp_core::crypto::AccountId32").jsonPrimitive.content)
        assertEquals(result, AssetHubRuntimeTypes.forChain(AssetHubStaking.WESTEND, result))
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<TypeDefinitionsTreeV2>(result)
        assertEquals(0, decoded.types.size)
    }

    @Test fun `known Hub aliases preserve other configured types and unrelated networks byte for byte`() {
        val original = """{"types":{},"versioning":[{"runtime_range":[0,null],"types":{"Other":"u16","Address":"old"}}]}"""
        val result = AssetHubRuntimeTypes.forChain(AssetHubStaking.POLKADOT, original)!!
        val types = Json.parseToJsonElement(result).jsonObject.getValue("versioning").jsonArray.single().jsonObject.getValue("types").jsonObject
        assertEquals("u16", types.getValue("Other").jsonPrimitive.content)
        assertEquals("sp_runtime::multiaddress::MultiAddress", types.getValue("Address").jsonPrimitive.content)
        assertEquals(result, AssetHubRuntimeTypes.forChain(AssetHubStaking.POLKADOT, result))
        assertEquals(original, AssetHubRuntimeTypes.forChain("unknown", original))
        assertNull(AssetHubRuntimeTypes.forChain("unknown", null))
    }
}
