package jp.co.soramitsu.coredb.model.cache

import java.math.BigInteger

/** Values freshly obtained from a provider. Fields not present in a variant are never written. */
sealed class FreshAssetCacheUpdate {
    data class Free(val free: BigInteger?) : FreshAssetCacheUpdate()
    data class FreeAndStatus(val free: BigInteger?, val status: String?) : FreshAssetCacheUpdate()
    data class AccountInfo(
        val free: BigInteger?, val reserved: BigInteger?, val miscFrozen: BigInteger?, val feeFrozen: BigInteger?
    ) : FreshAssetCacheUpdate()
    data class Orml(val free: BigInteger?, val reserved: BigInteger?, val miscFrozen: BigInteger?) : FreshAssetCacheUpdate()
    data class Staking(val redeemable: BigInteger?, val unbonding: BigInteger?, val bonded: BigInteger?) : FreshAssetCacheUpdate()
}

/** Updates never materialize untouched cached numbers, including malformed text or blobs. */
object FreshAssetWriteSql {
    private const val IDENTITY = " WHERE metaId = :metaId AND accountId = :accountId AND chainId = :chainId AND id = :assetId"
    const val MOVE_EMPTY_IF_ABSENT = """UPDATE assets SET accountId = :accountId
        WHERE metaId = :metaId AND accountId = :emptyAccountId AND chainId = :chainId AND id = :assetId
        AND NOT EXISTS (SELECT 1 FROM assets AS target WHERE target.metaId = :metaId
            AND target.accountId = :accountId AND target.chainId = :chainId AND target.id = :assetId)"""
    const val FREE = "UPDATE assets SET tokenPriceId = :tokenPriceId, freeInPlanks = :free" + IDENTITY
    const val FREE_AND_STATUS = "UPDATE assets SET tokenPriceId = :tokenPriceId, freeInPlanks = :free, status = :status" + IDENTITY
    const val ACCOUNT_INFO = "UPDATE assets SET tokenPriceId = :tokenPriceId, freeInPlanks = :free, reservedInPlanks = :reserved, miscFrozenInPlanks = :miscFrozen, feeFrozenInPlanks = :feeFrozen" + IDENTITY
    const val ORML = "UPDATE assets SET tokenPriceId = :tokenPriceId, freeInPlanks = :free, reservedInPlanks = :reserved, miscFrozenInPlanks = :miscFrozen" + IDENTITY
    const val STAKING = "UPDATE assets SET tokenPriceId = :tokenPriceId, redeemableInPlanks = :redeemable, unbondingInPlanks = :unbonding, bondedInPlanks = :bonded" + IDENTITY
    const val MARK_NOT_NEED = "UPDATE assets SET markedNotNeed = 1" + IDENTITY
}
