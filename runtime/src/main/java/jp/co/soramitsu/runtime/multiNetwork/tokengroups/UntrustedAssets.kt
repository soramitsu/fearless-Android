package jp.co.soramitsu.runtime.multiNetwork.tokengroups

import com.google.gson.JsonObject
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.asObject
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.requireKeys
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.requiredInt
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.requiredString

/**
 * Registry assets whose registry price must not be trusted: `untrustedAssets` in the shared groups
 * file (contract `docs/portfolio-contract.md` §3), for example a third-party token the registry
 * prices as Tether. A listed asset has no trusted price whatever `priceId` the registry gives it:
 * it adds 0 fiat, counts as an asset without price and is never part of a token group.
 */
class UntrustedAssets(val entries: List<UntrustedAssetDefinition>) {

    private val identities: Set<String> = entries.mapTo(mutableSetOf()) { identity(it.chainId, it.assetId) }

    fun contains(chainId: ChainId, assetId: String): Boolean = identity(chainId, assetId) in identities

    companion object {
        val EMPTY = UntrustedAssets(emptyList())

        private const val KEY = "untrustedAssets"
        private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        private val ENTRY_KEYS = setOf(
            "chainId", "assetId", "symbol", "precision", "network", "registryPriceId", "reviewedAt", "reason"
        )

        /** The list as the catalog reads it: the key is optional, every entry is in the reviewed shape. */
        internal fun parse(root: JsonObject): UntrustedAssets {
            val listed = root.get(KEY) ?: return EMPTY
            require(listed.isJsonArray) { "$KEY must be an array" }
            val entries = listed.asJsonArray.map { parseEntry(it.asObject("untrusted asset")) }
            require(entries.distinctBy { identity(it.chainId, it.assetId) }.size == entries.size) {
                "Duplicate untrusted asset"
            }

            return UntrustedAssets(entries)
        }

        /**
         * Reads only the list, from a groups file the catalog parser rejected. A group this build
         * cannot read must never bring a distrusted price back, so every entry that names a chain
         * and an asset is kept and everything else in the file is ignored.
         */
        fun salvage(bytes: ByteArray): UntrustedAssets {
            val listed = SharedPortfolioJson.parseObject(bytes).get(KEY) ?: return EMPTY
            require(listed.isJsonArray) { "$KEY must be an array" }

            return UntrustedAssets(
                listed.asJsonArray.mapNotNull { element ->
                    runCatching { element.asObject("untrusted asset") }.getOrNull()?.let { entry ->
                        val chainId = entry.stringOrNull("chainId") ?: return@mapNotNull null
                        val assetId = entry.stringOrNull("assetId") ?: return@mapNotNull null
                        UntrustedAssetDefinition(
                            chainId = chainId,
                            assetId = assetId,
                            symbol = entry.stringOrNull("symbol").orEmpty(),
                            registryPriceId = entry.stringOrNull("registryPriceId").orEmpty()
                        )
                    }
                }
            )
        }

        private fun parseEntry(json: JsonObject): UntrustedAssetDefinition {
            json.requireKeys(where = "untrusted asset", allowed = ENTRY_KEYS, required = ENTRY_KEYS)
            json.requiredString("network")
            json.requiredString("reason")
            require(json.requiredInt("precision") >= 0) { "Untrusted asset precision must not be negative" }
            require(json.requiredString("reviewedAt").matches(DATE)) { "untrusted asset reviewedAt must be YYYY-MM-DD" }

            return UntrustedAssetDefinition(
                chainId = json.requiredString("chainId"),
                assetId = json.requiredString("assetId"),
                symbol = json.requiredString("symbol"),
                registryPriceId = json.requiredString("registryPriceId")
            )
        }

        private fun JsonObject.stringOrNull(name: String): String? {
            val value = get(name) ?: return null
            return value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf(String::isNotEmpty)
        }

        /**
         * Case-folded, although only EVM contract ids compare case-insensitively: folding every id
         * can only take a price away from an asset, never give a listed asset its price back.
         */
        private fun identity(chainId: ChainId, assetId: String): String = "$chainId\u0000${assetId.lowercase()}"
    }
}

data class UntrustedAssetDefinition(
    val chainId: ChainId,
    /** Exactly the registry `assets[].id`. */
    val assetId: String,
    val symbol: String,
    /** The registry priceId that must not be used. Review evidence; the asset is distrusted whatever the registry says now. */
    val registryPriceId: String
)
