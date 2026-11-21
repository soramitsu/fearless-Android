package jp.co.soramitsu.runtime.assethub

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Current native aliases for genesis-bound Hubs, including a Hub the types catalogue does not list. */
object AssetHubRuntimeTypes {
    private val requiredExtensions = setOf(
        "CheckSpecVersion", "CheckTxVersion", "CheckGenesis", "CheckMortality", "CheckNonce", "CheckMetadataHash"
    )
    private val namespaces = mapOf(
        AssetHubStaking.POLKADOT to "asset_hub_polkadot_runtime",
        AssetHubStaking.KUSAMA to "asset_hub_kusama_runtime",
        AssetHubStaking.WESTEND to "asset_hub_westend_runtime"
    )

    fun forChain(chainId: String, configured: String?): String? {
        val namespace = namespaces[chainId] ?: return configured
        val original = configured?.let { Json.parseToJsonElement(it).jsonObject }.orEmpty()
        val aliases = mapOf(
            "Balance" to "u128", "Index" to "u32", "Phase" to "frame_system::Phase",
            "Address" to "sp_runtime::multiaddress::MultiAddress", "ExtrinsicSignature" to "sp_runtime::MultiSignature",
            "sp_core::crypto::AccountId32" to "GenericAccountId",
            "$namespace::RuntimeCall" to "GenericCall", "$namespace::RuntimeEvent" to "GenericEvent"
        ).mapValues { JsonPrimitive(it.value) }
        val defaultVersion = JsonObject(mapOf("runtime_range" to JsonArray(listOf(JsonPrimitive(0), JsonNull))))
        val versions = (original["versioning"] as? JsonArray)?.takeIf { it.isNotEmpty() } ?: JsonArray(listOf(defaultVersion))
        val augmented = versions.map { element ->
            val version = element.jsonObject
            JsonObject(version + ("types" to JsonObject(version["types"]?.jsonObject.orEmpty() + aliases)))
        }
        return JsonObject(
            original + mapOf(
                "chainId" to JsonPrimitive(chainId),
                "types" to (original["types"] ?: JsonObject(emptyMap())),
                "versioning" to JsonArray(augmented)
            )
        ).toString()
    }

    fun requireSigningExtensions(chainId: String, extensions: List<String>) {
        if (chainId in namespaces) requireSigningExtensions(extensions)
    }

    /** A staking runtime must sign over its genesis, versions, mortality, nonce, metadata mode and native fee. */
    fun requireSigningExtensions(extensions: List<String>) {
        require(extensions.size == extensions.toSet().size && extensions.containsAll(requiredExtensions)) {
            "Asset Hub signing extensions are incomplete or duplicated"
        }
        require("ChargeAssetTxPayment" in extensions || "ChargeTransactionPayment" in extensions) {
            "Asset Hub native fee extension is missing"
        }
    }
}
