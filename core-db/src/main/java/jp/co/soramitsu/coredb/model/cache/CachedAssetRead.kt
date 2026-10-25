package jp.co.soramitsu.coredb.model.cache

import androidx.room.Embedded
import java.math.BigInteger
import java.math.BigDecimal

/** These read-only projections deliberately do not inherit or produce the mutable AssetLocal entity. */
data class RawCachedAssetRow(
    val id: String,
    val chainId: String,
    val accountId: ByteArray,
    val metaId: Long,
    val tokenPriceId: String?,
    val sortIndex: Int,
    val enabled: Boolean?,
    val markedNotNeed: Boolean,
    val chainAccountName: String?,
    val status: String?,
    @Embedded(prefix = "free_") val free: CachedNumericCell,
    @Embedded(prefix = "reserved_") val reserved: CachedNumericCell,
    @Embedded(prefix = "miscFrozen_") val miscFrozen: CachedNumericCell,
    @Embedded(prefix = "feeFrozen_") val feeFrozen: CachedNumericCell,
    @Embedded(prefix = "bonded_") val bonded: CachedNumericCell,
    @Embedded(prefix = "redeemable_") val redeemable: CachedNumericCell,
    @Embedded(prefix = "unbonding_") val unbonding: CachedNumericCell
) {
    fun decode(): CachedAssetRead = CachedAssetRead(
        id, chainId, accountId.copyOf(), metaId, tokenPriceId, sortIndex, enabled,
        markedNotNeed, chainAccountName, status,
        CachedNumericDecoder.integer(free),
        CachedNumericDecoder.integer(reserved),
        CachedNumericDecoder.integer(miscFrozen),
        CachedNumericDecoder.integer(feeFrozen),
        CachedNumericDecoder.integer(bonded),
        CachedNumericDecoder.integer(redeemable),
        CachedNumericDecoder.integer(unbonding)
    )
}

data class CachedAssetRead(
    val id: String,
    val chainId: String,
    val accountId: ByteArray,
    val metaId: Long,
    val tokenPriceId: String?,
    val sortIndex: Int,
    val enabled: Boolean?,
    val markedNotNeed: Boolean,
    val chainAccountName: String?,
    val status: String?,
    val free: CachedNumericState<BigInteger>,
    val reserved: CachedNumericState<BigInteger>,
    val miscFrozen: CachedNumericState<BigInteger>,
    val feeFrozen: CachedNumericState<BigInteger>,
    val bonded: CachedNumericState<BigInteger>,
    val redeemable: CachedNumericState<BigInteger>,
    val unbonding: CachedNumericState<BigInteger>
) {
    /** Empty-account lookup adaptation changes identity only; every availability state survives. */
    fun withRequestedAccount(account: ByteArray): CachedAssetRead = copy(accountId = account.copyOf())
}

data class RawCachedAssetWithPriceRow(
    @Embedded val asset: RawCachedAssetRow,
    val priceId: String?,
    val fiatSymbol: String?,
    @Embedded(prefix = "fiatRate_") val fiatRate: CachedNumericCell,
    @Embedded(prefix = "recentRateChange_") val recentRateChange: CachedNumericCell,
    @Embedded(prefix = "fiatCurrencyId_") val fiatCurrencyId: CachedNumericCell = CachedNumericCell("null", 0, null)
) {
    fun decode(): CachedAssetWithPriceRead = CachedAssetWithPriceRead(
        asset.decode(),
        priceId?.let {
            CachedTokenPriceRead(it, fiatSymbol, CachedNumericDecoder.decimal(fiatRate), CachedNumericDecoder.decimal(recentRateChange), CachedFiatCurrencyDecoder.decode(fiatCurrencyId))
        }
    )
}

data class CachedAssetWithPriceRead(val asset: CachedAssetRead, val token: CachedTokenPriceRead?) {
    fun withRequestedAccount(account: ByteArray): CachedAssetWithPriceRead = copy(asset = asset.withRequestedAccount(account))
}

data class CachedTokenPriceRead(
    val priceId: String,
    val fiatSymbol: String?,
    val fiatRate: CachedNumericState<BigDecimal>,
    val recentRateChange: CachedNumericState<BigDecimal>,
    val fiatCurrencyIdentity: CachedFiatCurrencyIdentity = CachedFiatCurrencyIdentity.Unverified
)
