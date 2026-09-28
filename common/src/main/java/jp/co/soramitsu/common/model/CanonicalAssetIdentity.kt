package jp.co.soramitsu.common.model

/** Network-scoped identity used for presentation, preferences, and price attribution. */
class AssetKey(
    ecosystem: String,
    chainId: String,
    assetId: String
) {
    val ecosystem: String = ecosystem.trim().lowercase()
    val chainId: String = chainId.trim().lowercase()
    val assetId: String = normalizeAssetId(this.ecosystem, assetId.trim())
    val serialized: String = listOf(this.ecosystem, this.chainId, this.assetId).joinToString(DELIMITER)

    override fun equals(other: Any?): Boolean {
        return other is AssetKey && serialized == other.serialized
    }

    override fun hashCode(): Int = serialized.hashCode()

    override fun toString(): String = serialized

    private companion object {
        const val DELIMITER = ":"

        fun normalizeAssetId(ecosystem: String, assetId: String): String {
            val isEvmEcosystem = ecosystem == "ethereum" || ecosystem == "ethereumbased" || ecosystem == "evm"
            val isContractAddress = assetId.length > 2 && assetId.startsWith("0x", ignoreCase = true) &&
                assetId.drop(2).all { it.isHexDigit() }
            return if (isEvmEcosystem && isContractAddress) assetId.lowercase() else assetId
        }

        fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}

/** Compatibility name for code migrated before the cross-client contract was finalized. */
typealias CanonicalAssetIdentity = AssetKey

enum class AssetMetadataTrust {
    Verified,
    Unverified,
    Missing
}

enum class AssetMetadataSource {
    Registry,
    Chain,
    Indexer
}

enum class PriceTrust {
    Canonical,
    CuratedGroup,
    Untrusted
}

/** Per-wallet presentation choice. Hidden assets are still included in trusted net worth. */
enum class AssetPreference {
    Auto,
    Shown,
    Hidden
}

enum class AssetDiscoveryCoverage {
    Complete,
    CatalogOnly,
    Limited
}

data class NetworkScanState(
    val chainId: String,
    val lastAttemptMillis: Long? = null,
    val lastSuccessMillis: Long? = null,
    val isStale: Boolean = false,
    val errorMessage: String? = null,
    val coverage: AssetDiscoveryCoverage
)

data class NetworkScanKey(
    val walletId: Long,
    val chainId: String
)
