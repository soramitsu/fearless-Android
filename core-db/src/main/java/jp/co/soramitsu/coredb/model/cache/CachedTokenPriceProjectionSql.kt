package jp.co.soramitsu.coredb.model.cache

/** Same bounded numeric projection as joined asset prices, with unchanged standalone predicates. */
object CachedTokenPriceProjectionSql {
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
    // Monetary readers obtain labels from their captured selected-currency context.
    private const val SYMBOL_FREE_PRICE_COLUMNS = """
tp.priceId,
NULL AS fiatSymbol,
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
    const val BY_ID = "SELECT " + PRICE_COLUMNS + " FROM token_price tp WHERE tp.priceId = :priceId"
    const val BY_SYMBOL = "SELECT " + PRICE_COLUMNS + " FROM token_price tp WHERE tp.fiatSymbol = :symbol"
    const val BY_IDS = "SELECT " + SYMBOL_FREE_PRICE_COLUMNS + " FROM token_price tp WHERE tp.priceId IN (:priceIds) ORDER BY tp.priceId COLLATE BINARY"
}
