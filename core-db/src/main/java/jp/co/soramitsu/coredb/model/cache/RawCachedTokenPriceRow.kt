package jp.co.soramitsu.coredb.model.cache

import androidx.room.Embedded

/** Read-only standalone price row; no numeric payload enters a mutable write entity. */
data class RawCachedTokenPriceRow(
    val priceId: String,
    val fiatSymbol: String?,
    @Embedded(prefix = "fiatRate_") val fiatRate: CachedNumericCell,
    @Embedded(prefix = "recentRateChange_") val recentRateChange: CachedNumericCell,
    @Embedded(prefix = "fiatCurrencyId_") val fiatCurrencyId: CachedNumericCell = CachedNumericCell("null", 0, null)
) {
    fun decode(): CachedTokenPriceRead = CachedTokenPriceRead(
        priceId, fiatSymbol, CachedNumericDecoder.decimal(fiatRate), CachedNumericDecoder.decimal(recentRateChange),
        CachedFiatCurrencyDecoder.decode(fiatCurrencyId)
    )
}
