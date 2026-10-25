package jp.co.soramitsu.runtime.multiNetwork.tokengroups

import com.google.gson.JsonObject
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.asObject
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.nullableString
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.optionalString
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.requireFormat
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.requireKeys
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.requiredArray
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.requiredInt
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.requiredObject
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.requiredString

/**
 * Curated token groups shared by Android, iOS and the extension: the workspace root's
 * `config/token-groups/canonical-asset-groups.v1.json`, bundled byte-for-byte as an APK asset
 * and pinned by sha256 in `SharedPortfolioConfigPinTest`. There is no remote override in 4.2.0.
 *
 * A group only says which exact (chainId, assetId) registry rows may be shown as one combined
 * row. It never supplies a price, and nothing is ever grouped by symbol or priceId alone.
 */
data class TokenGroupCatalog(
    val registry: SharedRegistryReference,
    val groups: List<TokenGroupDefinition>,
    /** Assets without a trusted price. They are never grouped, whatever a group lists. */
    val untrustedAssets: UntrustedAssets = UntrustedAssets.EMPTY
) {
    /**
     * Approved groups whose curated data is consistent. A member listed twice (in any groups,
     * approved or proposed) invalidates every group containing it, an approved group whose
     * members declare different precisions is never summed, and a group listing an asset without
     * a trusted price is never rendered; all three fall back to separate rows.
     */
    val renderableGroups: List<TokenGroupDefinition> = run {
        val sharedMemberGroupIds = groups
            .flatMap { group -> group.members.map { member -> member.duplicateIdentity to group.id } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .values
            .filter { owners -> owners.size > 1 }
            .flatten()
            .toSet()

        groups.filter { group ->
            group.status == TokenGroupStatus.Approved &&
                group.id !in sharedMemberGroupIds &&
                group.members.none { untrustedAssets.contains(it.chainId, it.assetId) } &&
                group.members.map(TokenGroupMemberDefinition::precision).distinct().size == 1
        }
    }

    companion object {
        const val FORMAT = "fearless.canonical-asset-groups"
        const val VERSION = 1
        const val MAX_GROUPS = 200
        const val MAX_MEMBERS = 64

        private val GROUP_ID = Regex("^[a-z0-9][a-z0-9-]{0,31}$")

        fun parse(bytes: ByteArray): TokenGroupCatalog {
            val root = SharedPortfolioJson.parseObject(bytes)
            root.requireKeys(
                where = "catalog",
                allowed = setOf("format", "version", "registry", "issuerSources", "untrustedAssets", "groups"),
                required = setOf("format", "version", "registry", "groups")
            )
            root.requireFormat(FORMAT, VERSION)
            val registry = SharedPortfolioJson.parseRegistryReference(root.requiredObject("registry"))
            // Issuer deployment lists are review evidence for the validator; the app only needs the members.
            root.get("issuerSources")?.asObject("issuerSources")
            val untrustedAssets = UntrustedAssets.parse(root)

            val groupsJson = root.requiredArray("groups")
            require(groupsJson.size() <= MAX_GROUPS) { "At most $MAX_GROUPS token groups" }
            val groups = groupsJson.map { parseGroup(it.asObject("group")) }
            require(groups.distinctBy(TokenGroupDefinition::id).size == groups.size) { "Duplicate token group id" }

            return TokenGroupCatalog(registry, groups, untrustedAssets)
        }

        private fun parseGroup(json: JsonObject): TokenGroupDefinition {
            json.requireKeys(
                where = "group",
                allowed = setOf("id", "symbol", "name", "status", "policy", "priceId", "symbolAliases", "issuerSource", "review", "members"),
                required = setOf("id", "symbol", "name", "status", "policy", "priceId", "review", "members")
            )
            val id = json.requiredString("id")
            require(id.matches(GROUP_ID)) { "Token group id $id must match $GROUP_ID" }
            json.requiredString("policy")
            json.requiredString("review")
            json.optionalString("issuerSource")

            val status = when (json.requiredString("status")) {
                "approved" -> TokenGroupStatus.Approved
                "proposed" -> TokenGroupStatus.Proposed
                else -> throw IllegalArgumentException("Token group $id has an unknown status")
            }
            val symbolAliases = json.get("symbolAliases")?.let { aliases ->
                require(aliases.isJsonArray) { "Token group $id symbolAliases must be an array" }
                aliases.asJsonArray.map { alias ->
                    require(alias.isJsonPrimitive && alias.asJsonPrimitive.isString && alias.asString.isNotEmpty()) {
                        "Token group $id symbolAliases must be non-empty strings"
                    }
                    alias.asString
                }
            }.orEmpty()
            val membersJson = json.requiredArray("members")
            require(membersJson.size() in 1..MAX_MEMBERS) { "Token group $id must have 1..$MAX_MEMBERS members" }

            return TokenGroupDefinition(
                id = id,
                symbol = json.requiredString("symbol"),
                name = json.requiredString("name"),
                status = status,
                priceId = json.nullableString("priceId"),
                symbolAliases = symbolAliases,
                members = membersJson.map { parseMember(it.asObject("member")) }
            )
        }

        private fun parseMember(json: JsonObject): TokenGroupMemberDefinition {
            json.requireKeys(
                where = "member",
                allowed = setOf("chainId", "assetId", "symbol", "precision", "role", "network", "kind", "issuerReference"),
                required = setOf("chainId", "assetId", "symbol", "precision", "role", "network")
            )
            json.requiredString("role")
            json.requiredString("network")
            json.optionalString("issuerReference")
            val precision = json.requiredInt("precision")
            require(precision >= 0) { "Member precision must not be negative" }

            return TokenGroupMemberDefinition(
                chainId = json.requiredString("chainId"),
                assetId = json.requiredString("assetId"),
                symbol = json.requiredString("symbol"),
                precision = precision,
                kind = json.optionalString("kind")
            )
        }
    }
}

data class TokenGroupDefinition(
    val id: String,
    val symbol: String,
    val name: String,
    val status: TokenGroupStatus,
    /** Validation only: every active member's registry priceId must equal it. Never used to price. */
    val priceId: String?,
    /** Reviewed member symbols that differ from [symbol] (for example xcDOT on Moonbeam). */
    val symbolAliases: List<String>,
    val members: List<TokenGroupMemberDefinition>
) {
    fun acceptsSymbol(symbol: String): Boolean {
        return symbol.equals(this.symbol, ignoreCase = true) ||
            symbolAliases.any { it.equals(symbol, ignoreCase = true) }
    }
}

enum class TokenGroupStatus {
    Approved,
    Proposed
}

data class TokenGroupMemberDefinition(
    val chainId: String,
    /** Exactly the registry `assets[].id`; EVM contract ids compare case-insensitively. */
    val assetId: String,
    val symbol: String,
    val precision: Int,
    /** How the member is matched; absent means registry. v1 skips members of any other kind. */
    val kind: String?
) {
    val isRegistryMember: Boolean
        get() = kind == null || kind == REGISTRY_MEMBER_KIND

    /**
     * Case-folded so a duplicate is caught even when only the spelling of an EVM contract id
     * differs. Folding non-EVM ids as well can only split groups, never merge them.
     */
    internal val duplicateIdentity: String
        get() = "$chainId\u0000${assetId.lowercase()}"

    private companion object {
        const val REGISTRY_MEMBER_KIND = "registry"
    }
}
