package jp.co.soramitsu.runtime.multiNetwork.tokengroups

import com.google.gson.JsonObject
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.asObject
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.nullableInt
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.requireFormat
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.requireKeys
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.requiredArray
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.requiredObject
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioJson.requiredString

/**
 * The three shared portfolio files every app bundles byte-for-byte (workspace root
 * `config/portfolio/app-copies.v1.json`, contract in `docs/portfolio-contract.md`).
 *
 * Each part loads on its own. A part that failed to load is null and its consumer falls back as
 * the contract says: no token groups (every asset is its own row), the registry `testnet` option
 * alone, and the computed zero rule (primary utility asset of a ranked mainnet).
 *
 * [untrustedAssets] comes from the groups file but outlives its groups: it is still read when the
 * catalog is rejected. It is null only when that file is unusable as a whole, and then no asset
 * is listed.
 */
class SharedPortfolioConfig(
    val tokenGroups: TokenGroupCatalog?,
    val testnetChainIds: TestnetChainIds?,
    val defaultVisibleAtZero: DefaultVisibleAtZero?,
    val untrustedAssets: UntrustedAssets?
) {
    /** isTestnet(chain) = 'testnet' in chain.options || chain.chainId in testnetChainIds. */
    fun isTestnet(chain: Chain): Boolean {
        return chain.isTestNet || testnetChainIds?.contains(chain.id) == true
    }

    /** A listed asset has no trusted price, whatever priceId the registry gives it. */
    fun isPriceUntrusted(chainId: ChainId, assetId: String): Boolean {
        return untrustedAssets?.contains(chainId, assetId) == true
    }
}

/** Networks treated as testnets in addition to registry chains with the `testnet` option. */
class TestnetChainIds(
    val registry: SharedRegistryReference,
    val chainIds: Set<ChainId>
) {
    fun contains(chainId: ChainId): Boolean = chainId in chainIds

    companion object {
        const val FORMAT = "fearless.testnet-chain-ids"
        const val VERSION = 1

        fun parse(bytes: ByteArray): TestnetChainIds {
            val root = SharedPortfolioJson.parseObject(bytes)
            root.requireKeys(
                where = "testnet chain ids",
                allowed = setOf("format", "version", "registry", "rule", "chainIds"),
                required = setOf("format", "version", "registry", "rule", "chainIds")
            )
            root.requireFormat(FORMAT, VERSION)
            root.requiredString("rule")
            val registry = SharedPortfolioJson.parseRegistryReference(root.requiredObject("registry"))

            val chainIds = root.requiredArray("chainIds").map { element ->
                val entry = element.asObject("testnet chain id")
                entry.requireKeys(
                    where = "testnet chain id",
                    allowed = setOf("chainId", "name", "source", "registryTestnetOption", "reason"),
                    required = setOf("chainId", "name", "source", "reason")
                )
                entry.requiredString("name")
                entry.requiredString("reason")
                require(entry.requiredString("source") in setOf("registry", "app-owned")) { "Unknown testnet source" }
                entry.get("registryTestnetOption")?.let {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) { "registryTestnetOption must be a boolean" }
                }
                entry.requiredString("chainId")
            }
            require(chainIds.isNotEmpty()) { "The testnet list must not be empty" }

            return TestnetChainIds(registry, chainIds.toSet())
        }
    }
}

/**
 * The listed part of the one zero rule: Auto assets shown at zero balance for wallets that hold
 * an account in the entry's ecosystem. The rest of the rule (testnets, send vetoes, favourite or
 * single selected network) is applied by the portfolio, not here.
 */
class DefaultVisibleAtZero(
    val registry: SharedRegistryReference,
    val entries: List<DefaultVisibleAtZeroEntry>
) {
    fun contains(chainId: ChainId, assetId: String): Boolean = entries.any { it.matches(chainId, assetId) }

    companion object {
        const val FORMAT = "fearless.default-visible-at-zero"
        const val VERSION = 1

        fun parse(bytes: ByteArray): DefaultVisibleAtZero {
            val root = SharedPortfolioJson.parseObject(bytes)
            root.requireKeys(
                where = "default visible at zero",
                allowed = setOf("format", "version", "registry", "rule", "entries"),
                required = setOf("format", "version", "registry", "rule", "entries")
            )
            root.requireFormat(FORMAT, VERSION)
            root.requiredString("rule")
            val registry = SharedPortfolioJson.parseRegistryReference(root.requiredObject("registry"))

            val entries = root.requiredArray("entries").map { parseEntry(it.asObject("zero default")) }
            require(entries.isNotEmpty()) { "The zero-default list must not be empty" }
            require(entries.distinctBy { it.chainId to it.assetId.lowercase() }.size == entries.size) {
                "Duplicate zero-default entry"
            }

            return DefaultVisibleAtZero(registry, entries)
        }

        private fun parseEntry(json: JsonObject): DefaultVisibleAtZeroEntry {
            json.requireKeys(
                where = "zero default",
                allowed = setOf("chainId", "assetId", "symbol", "network", "ecosystem", "reason", "rank"),
                required = setOf("chainId", "assetId", "symbol", "network", "ecosystem", "reason", "rank")
            )
            json.requiredString("network")
            val ecosystemName = json.requiredString("ecosystem")
            val reasonName = json.requiredString("reason")

            return DefaultVisibleAtZeroEntry(
                chainId = json.requiredString("chainId"),
                assetId = json.requiredString("assetId"),
                symbol = json.requiredString("symbol"),
                ecosystem = requireNotNull(SharedPortfolioEcosystem.fromWireName(ecosystemName)) {
                    "Unknown zero-default ecosystem $ecosystemName"
                },
                reason = requireNotNull(DefaultVisibleAtZeroEntry.Reason.fromWireName(reasonName)) {
                    "Unknown zero-default reason $reasonName"
                },
                rank = json.nullableInt("rank")
            )
        }
    }
}

data class DefaultVisibleAtZeroEntry(
    val chainId: ChainId,
    val assetId: String,
    val symbol: String,
    /** The wallet must hold an account in this ecosystem for the entry to apply. */
    val ecosystem: SharedPortfolioEcosystem,
    val reason: Reason,
    val rank: Int?
) {
    fun matches(chainId: ChainId, assetId: String): Boolean {
        return this.chainId == chainId &&
            this.assetId.equals(assetId, ignoreCase = ecosystem.comparesAssetIdsIgnoringCase)
    }

    enum class Reason(val wireName: String) {
        RankedMainnetUtility("ranked-mainnet-utility"),
        EcosystemPrimary("ecosystem-primary");

        companion object {
            fun fromWireName(value: String): Reason? = entries.firstOrNull { it.wireName == value }
        }
    }
}

/** Ecosystem names used by the shared portfolio files. */
enum class SharedPortfolioEcosystem(val wireName: String) {
    Substrate("substrate"),
    Ethereum("ethereum"),
    EthereumBased("ethereumBased"),
    Ton("ton"),
    Bitcoin("bitcoin"),
    Solana("solana"),
    Iroha("iroha");

    /** EVM asset ids are contract addresses and compare case-insensitively; every other id exactly. */
    val comparesAssetIdsIgnoringCase: Boolean
        get() = this == Ethereum || this == EthereumBased

    companion object {
        fun fromWireName(value: String): SharedPortfolioEcosystem? = entries.firstOrNull { it.wireName == value }
    }
}
