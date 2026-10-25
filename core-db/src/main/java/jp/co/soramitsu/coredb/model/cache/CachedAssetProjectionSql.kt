package jp.co.soramitsu.coredb.model.cache

/** Numeric-only bounding proposal: metadata and list cardinality are separate reviewed constraints. */
object CachedAssetProjectionSql {
    private const val ASSET_COLUMNS = """
a.id,
a.chainId,
a.accountId,
a.metaId,
a.tokenPriceId,
a.sortIndex,
a.enabled,
a.markedNotNeed,
a.chainAccountName,
a.status,
typeof(a.freeInPlanks) AS free_storageClass,
CASE WHEN a.freeInPlanks IS NULL THEN 0 ELSE length(CAST(a.freeInPlanks AS BLOB)) END AS free_byteCount,
CASE WHEN typeof(a.freeInPlanks) = 'text' AND length(CAST(a.freeInPlanks AS BLOB)) <= 4096 THEN CAST(a.freeInPlanks AS BLOB) ELSE NULL END AS free_payload,
typeof(a.reservedInPlanks) AS reserved_storageClass,
CASE WHEN a.reservedInPlanks IS NULL THEN 0 ELSE length(CAST(a.reservedInPlanks AS BLOB)) END AS reserved_byteCount,
CASE WHEN typeof(a.reservedInPlanks) = 'text' AND length(CAST(a.reservedInPlanks AS BLOB)) <= 4096 THEN CAST(a.reservedInPlanks AS BLOB) ELSE NULL END AS reserved_payload,
typeof(a.miscFrozenInPlanks) AS miscFrozen_storageClass,
CASE WHEN a.miscFrozenInPlanks IS NULL THEN 0 ELSE length(CAST(a.miscFrozenInPlanks AS BLOB)) END AS miscFrozen_byteCount,
CASE WHEN typeof(a.miscFrozenInPlanks) = 'text' AND length(CAST(a.miscFrozenInPlanks AS BLOB)) <= 4096 THEN CAST(a.miscFrozenInPlanks AS BLOB) ELSE NULL END AS miscFrozen_payload,
typeof(a.feeFrozenInPlanks) AS feeFrozen_storageClass,
CASE WHEN a.feeFrozenInPlanks IS NULL THEN 0 ELSE length(CAST(a.feeFrozenInPlanks AS BLOB)) END AS feeFrozen_byteCount,
CASE WHEN typeof(a.feeFrozenInPlanks) = 'text' AND length(CAST(a.feeFrozenInPlanks AS BLOB)) <= 4096 THEN CAST(a.feeFrozenInPlanks AS BLOB) ELSE NULL END AS feeFrozen_payload,
typeof(a.bondedInPlanks) AS bonded_storageClass,
CASE WHEN a.bondedInPlanks IS NULL THEN 0 ELSE length(CAST(a.bondedInPlanks AS BLOB)) END AS bonded_byteCount,
CASE WHEN typeof(a.bondedInPlanks) = 'text' AND length(CAST(a.bondedInPlanks AS BLOB)) <= 4096 THEN CAST(a.bondedInPlanks AS BLOB) ELSE NULL END AS bonded_payload,
typeof(a.redeemableInPlanks) AS redeemable_storageClass,
CASE WHEN a.redeemableInPlanks IS NULL THEN 0 ELSE length(CAST(a.redeemableInPlanks AS BLOB)) END AS redeemable_byteCount,
CASE WHEN typeof(a.redeemableInPlanks) = 'text' AND length(CAST(a.redeemableInPlanks AS BLOB)) <= 4096 THEN CAST(a.redeemableInPlanks AS BLOB) ELSE NULL END AS redeemable_payload,
typeof(a.unbondingInPlanks) AS unbonding_storageClass,
CASE WHEN a.unbondingInPlanks IS NULL THEN 0 ELSE length(CAST(a.unbondingInPlanks AS BLOB)) END AS unbonding_byteCount,
CASE WHEN typeof(a.unbondingInPlanks) = 'text' AND length(CAST(a.unbondingInPlanks AS BLOB)) <= 4096 THEN CAST(a.unbondingInPlanks AS BLOB) ELSE NULL END AS unbonding_payload
"""
    private const val PRICE_COLUMNS = """
tp.priceId,
tp.fiatSymbol,
typeof(tp.fiatCurrencyId) AS fiatCurrencyId_storageClass,
CASE WHEN tp.fiatCurrencyId IS NULL THEN 0 ELSE length(CAST(tp.fiatCurrencyId AS BLOB)) END AS fiatCurrencyId_byteCount,
CASE WHEN typeof(tp.fiatCurrencyId) = 'text' AND length(CAST(tp.fiatCurrencyId AS BLOB)) <= 4096 THEN CAST(tp.fiatCurrencyId AS BLOB) ELSE NULL END AS fiatCurrencyId_payload,
typeof(tp.fiatRate) AS fiatRate_storageClass,
CASE WHEN tp.fiatRate IS NULL THEN 0 ELSE length(CAST(tp.fiatRate AS BLOB)) END AS fiatRate_byteCount,
CASE WHEN typeof(tp.fiatRate) = 'text' AND length(CAST(tp.fiatRate AS BLOB)) <= 4096 THEN CAST(tp.fiatRate AS BLOB) ELSE NULL END AS fiatRate_payload,
typeof(tp.recentRateChange) AS recentRateChange_storageClass,
CASE WHEN tp.recentRateChange IS NULL THEN 0 ELSE length(CAST(tp.recentRateChange AS BLOB)) END AS recentRateChange_byteCount,
CASE WHEN typeof(tp.recentRateChange) = 'text' AND length(CAST(tp.recentRateChange AS BLOB)) <= 4096 THEN CAST(tp.recentRateChange AS BLOB) ELSE NULL END AS recentRateChange_payload
"""
    const val BALANCES = "SELECT " + ASSET_COLUMNS + " FROM assets a WHERE a.metaId = :metaId"
    const val ACCOUNT_ASSETS = "SELECT " + ASSET_COLUMNS + ", " + PRICE_COLUMNS + """
        FROM assets a LEFT JOIN token_price tp ON a.tokenPriceId = tp.priceId
        LEFT JOIN chain_accounts ca ON ca.metaId = a.metaId AND ca.chainId = a.chainId
        WHERE a.metaId = :metaId AND (ca.accountId = a.accountId OR ca.accountId IS NULL)
        ORDER BY a.sortIndex
"""
    // Portfolio selection applies the current MetaAccount in Kotlin. Metadata readers keep ACCOUNT_ASSETS.
    const val PORTFOLIO_ASSETS = "SELECT " + ASSET_COLUMNS + ", " + PRICE_COLUMNS + """
        FROM assets a LEFT JOIN token_price tp ON a.tokenPriceId = tp.priceId
        WHERE a.metaId = :metaId ORDER BY a.sortIndex
"""
    // Same wallet/order scope as PORTFOLIO_ASSETS; no persisted quote metadata is hydrated.
    const val PORTFOLIO_BALANCES = "SELECT " + ASSET_COLUMNS + """
        FROM assets a WHERE a.metaId = :metaId ORDER BY a.sortIndex
"""
    const val ACCOUNT_ASSET = "SELECT " + ASSET_COLUMNS + ", " + PRICE_COLUMNS + """
        FROM assets a LEFT JOIN token_price tp ON a.tokenPriceId = tp.priceId
        WHERE a.accountId IN (:accountId, :emptyAccountId) AND a.chainId = :chainId AND a.id = :assetId
        AND a.metaId = :metaId ORDER BY CASE WHEN a.accountId = :accountId THEN 0 ELSE 1 END, a.sortIndex
"""
    const val SYMBOL_ASSETS = "SELECT " + ASSET_COLUMNS + ", " + PRICE_COLUMNS + """
        FROM assets a LEFT JOIN token_price tp ON a.tokenPriceId = tp.priceId
        LEFT JOIN chain_assets ca ON ca.id = a.id AND ca.chainId = a.chainId
        WHERE a.accountId IN (:accountId, :emptyAccountId) AND a.chainId = :chainId
        AND ca.symbol = :symbol AND a.metaId = :metaId
        AND (a.accountId = :accountId OR NOT EXISTS (
            SELECT 1 FROM assets exact WHERE exact.metaId = a.metaId AND exact.chainId = a.chainId
            AND exact.id = a.id AND exact.accountId = :accountId
        )) ORDER BY a.sortIndex
"""
    const val EXACT_CHAIN_ASSET = "SELECT " + ASSET_COLUMNS + ", " + PRICE_COLUMNS + """
        FROM assets a LEFT JOIN token_price tp ON a.tokenPriceId = tp.priceId
        INNER JOIN chain_assets ca ON ca.id = a.id AND ca.chainId = a.chainId
        WHERE a.metaId = :accountMetaId AND a.chainId = :chainId AND ca.id = :id
"""
}
