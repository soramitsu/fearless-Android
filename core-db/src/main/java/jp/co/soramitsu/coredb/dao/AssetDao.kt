package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import java.math.BigInteger
import jp.co.soramitsu.coredb.model.cache.FreshAssetCacheUpdate
import jp.co.soramitsu.coredb.model.cache.FreshAssetWriteSql
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.AssetPresentationLocal
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.coredb.model.cache.CachedAssetProjectionSql
import jp.co.soramitsu.coredb.model.cache.CachedAssetRead
import jp.co.soramitsu.coredb.model.cache.CachedAssetWithPriceRead
import jp.co.soramitsu.coredb.model.cache.RawCachedAssetRow
import jp.co.soramitsu.coredb.model.cache.RawCachedAssetWithPriceRow
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

val emptyAccountIdValue: AccountId = ByteArray(0)

interface AssetReadOnlyCache {

    fun observeAssets(metaId: Long): Flow<List<CachedAssetWithPriceRead>>

    /**
     * Chains holding, in any wallet, an explicitly shown asset or any asset with a positive balance:
     * every row the portfolio can show, and every balance it reports as hidden, needs a live
     * subscription. A hidden asset is still the user's money, and a deposit into it must be noticed.
     */
    fun observeChainIdsNeedingSync(): Flow<Set<String>>
    suspend fun getAssets(metaId: Long): List<CachedAssetWithPriceRead>

    fun observeAsset(metaId: Long, accountId: AccountId, chainId: String, assetId: String): Flow<CachedAssetWithPriceRead>

    suspend fun getAsset(metaId: Long, accountId: AccountId, chainId: String, assetId: String): CachedAssetWithPriceRead?
    suspend fun getAssets(metaId: Long, accountId: AccountId, chainId: String, symbol: String): List<CachedAssetWithPriceRead>
}

@Dao
abstract class AssetDao : AssetReadOnlyCache {

    /**
     * Read only explicit presentation state. The 32 KiB portable metadata bound cannot hold
     * more than 2,184 minimum-size rows; the extra row makes overflow observable without
     * loading an unbounded balance table. Generic rows may have an empty accountId blob.
     */
    @Query(
        """SELECT chainId, id AS assetId, accountId, enabled, sortIndex,
            markedNotNeed, chainAccountName,
            CAST(chainId AS BLOB) AS chainIdRaw,
            CAST(id AS BLOB) AS assetIdRaw,
            CAST(chainAccountName AS BLOB) AS chainAccountNameRaw,
            typeof(chainId) AS chainIdStorageClass,
            typeof(id) AS assetIdStorageClass,
            typeof(accountId) AS accountIdStorageClass,
            typeof(enabled) AS enabledStorageClass,
            typeof(sortIndex) AS sortIndexStorageClass,
            typeof(markedNotNeed) AS markedNotNeedStorageClass,
            typeof(chainAccountName) AS chainAccountNameStorageClass
            FROM assets WHERE metaId = :metaId
            AND (enabled IS NOT NULL OR sortIndex != 2147483647
                OR markedNotNeed != 0 OR chainAccountName IS NOT NULL)
            ORDER BY chainId COLLATE BINARY, id COLLATE BINARY, accountId
            LIMIT 2185"""
    )
    abstract suspend fun getExplicitAssetPresentation(metaId: Long): List<AssetPresentationLocal>

    fun observeBalances(metaId: Long): Flow<List<CachedAssetRead>> =
        observeBalanceRows(metaId).map { rows -> rows.map { it.decode() } }

    @Query(CachedAssetProjectionSql.BALANCES)
    protected abstract fun observeBalanceRows(metaId: Long): Flow<List<RawCachedAssetRow>>

    override fun observeAssets(metaId: Long): Flow<List<CachedAssetWithPriceRead>> =
        observeAccountAssetRows(metaId).map { rows -> rows.map { it.decode() } }

    @Query(CachedAssetProjectionSql.ACCOUNT_ASSETS)
    protected abstract fun observeAccountAssetRows(metaId: Long): Flow<List<RawCachedAssetWithPriceRow>>

    /** Current-account consumers choose exact/fallback rows after resolving the current chain account. */
    fun observePortfolioAssets(metaId: Long): Flow<List<CachedAssetWithPriceRead>> =
        observePortfolioAssetRows(metaId).map { rows -> rows.map { it.decode() } }

    suspend fun getPortfolioAssets(metaId: Long): List<CachedAssetWithPriceRead> =
        getPortfolioAssetRows(metaId).map { it.decode() }

    @Query(CachedAssetProjectionSql.PORTFOLIO_ASSETS)
    protected abstract fun observePortfolioAssetRows(metaId: Long): Flow<List<RawCachedAssetWithPriceRow>>

    @Query(CachedAssetProjectionSql.PORTFOLIO_ASSETS)
    protected abstract suspend fun getPortfolioAssetRows(metaId: Long): List<RawCachedAssetWithPriceRow>

    /** Asset-only portfolio read; current account/fallback selection belongs to the snapshot reader. */
    fun observePortfolioBalances(metaId: Long): Flow<List<CachedAssetRead>> =
        observePortfolioBalanceRows(metaId).map { rows -> rows.map { it.decode() } }

    suspend fun getPortfolioBalances(metaId: Long): List<CachedAssetRead> =
        getPortfolioBalanceRows(metaId).map { it.decode() }

    @Query(CachedAssetProjectionSql.PORTFOLIO_BALANCES)
    protected abstract fun observePortfolioBalanceRows(metaId: Long): Flow<List<RawCachedAssetRow>>

    @Query(CachedAssetProjectionSql.PORTFOLIO_BALANCES)
    protected abstract suspend fun getPortfolioBalanceRows(metaId: Long): List<RawCachedAssetRow>

    override fun observeChainIdsNeedingSync(): Flow<Set<String>> =
        observeChainIdsWithShownOrFundedAssets().map { it.toSet() }

    // Balances are stored as decimal text: NULL while loading, "-1" on a load error.
    @Query(
        """SELECT DISTINCT chainId FROM assets
            WHERE enabled = 1
            OR (freeInPlanks IS NOT NULL AND freeInPlanks NOT IN ('', '0') AND substr(freeInPlanks, 1, 1) != '-')
            OR (reservedInPlanks IS NOT NULL AND reservedInPlanks NOT IN ('', '0') AND substr(reservedInPlanks, 1, 1) != '-')"""
    )
    protected abstract fun observeChainIdsWithShownOrFundedAssets(): Flow<List<String>>

    override suspend fun getAssets(metaId: Long): List<CachedAssetWithPriceRead> =
        getAccountAssetRows(metaId).map { it.decode() }

    @Query(CachedAssetProjectionSql.ACCOUNT_ASSETS)
    protected abstract suspend fun getAccountAssetRows(metaId: Long): List<RawCachedAssetWithPriceRow>

    override fun observeAsset(metaId: Long, accountId: AccountId, chainId: String, assetId: String): Flow<CachedAssetWithPriceRead> =
        observeAssetWithEmpty(metaId, accountId, chainId, assetId, emptyAccountIdValue)
            .flowOn(Dispatchers.IO)
            .mapNotNull { it }
            .map { it.decode().withRequestedAccount(accountId) }

    @Query(CachedAssetProjectionSql.ACCOUNT_ASSET)
    protected abstract fun observeAssetWithEmpty(
        metaId: Long,
        accountId: AccountId,
        chainId: String,
        assetId: String,
        emptyAccountId: AccountId
    ): Flow<RawCachedAssetWithPriceRow?>

    override suspend fun getAsset(metaId: Long, accountId: AccountId, chainId: String, assetId: String): CachedAssetWithPriceRead? =
        getAssetWithEmpty(metaId, accountId, chainId, assetId, emptyAccountIdValue)?.decode()

    @Query(CachedAssetProjectionSql.ACCOUNT_ASSET)
    protected abstract suspend fun getAssetWithEmpty(
        metaId: Long,
        accountId: AccountId,
        chainId: String,
        assetId: String,
        emptyAccountId: AccountId
    ): RawCachedAssetWithPriceRow?

    override suspend fun getAssets(metaId: Long, accountId: AccountId, chainId: String, symbol: String): List<CachedAssetWithPriceRead> =
        getAssetsWithEmpty(metaId, accountId, chainId, symbol, emptyAccountIdValue).map { it.decode() }

    @Query(CachedAssetProjectionSql.SYMBOL_ASSETS)
    protected abstract suspend fun getAssetsWithEmpty(
        metaId: Long,
        accountId: AccountId,
        chainId: String,
        symbol: String,
        emptyAccountId: AccountId
    ): List<RawCachedAssetWithPriceRow>

    /**
     * Apply only a provider's fresh fields in one transaction. A generic row can move to the
     * requested account only when no exact row exists. SQLite retains every other stored value
     * and preference; an exact/generic collision keeps both rows and updates the exact row.
     * New rows use the nullable Auto visibility state, like discovery updates.
     */
    @Transaction
    open suspend fun updateFreshAssetCache(
        metaId: Long, accountId: AccountId, chainId: String, assetId: String,
        tokenPriceId: String?, fresh: FreshAssetCacheUpdate
    ) {
        val stableAccountId = accountId.copyOf()
        if (stableAccountId.isNotEmpty()) {
            moveEmptyAccountIfTargetAbsent(metaId, stableAccountId, chainId, assetId, ByteArray(0))
        }
        // IGNORE retains a concurrently inserted row and its presentation choices.
        insertAssets(listOf(AssetLocal.createEmpty(stableAccountId, assetId, chainId, metaId, tokenPriceId, enabled = null)))
        val updated = when (fresh) {
            is FreshAssetCacheUpdate.Free -> updateFreshFree(metaId, stableAccountId, chainId, assetId, tokenPriceId, fresh.free)
            is FreshAssetCacheUpdate.FreeAndStatus -> updateFreshFreeAndStatus(metaId, stableAccountId, chainId, assetId, tokenPriceId, fresh.free, fresh.status)
            is FreshAssetCacheUpdate.AccountInfo -> updateFreshAccountInfo(metaId, stableAccountId, chainId, assetId, tokenPriceId, fresh.free, fresh.reserved, fresh.miscFrozen, fresh.feeFrozen)
            is FreshAssetCacheUpdate.Orml -> updateFreshOrml(metaId, stableAccountId, chainId, assetId, tokenPriceId, fresh.free, fresh.reserved, fresh.miscFrozen)
            is FreshAssetCacheUpdate.Staking -> updateFreshStaking(metaId, stableAccountId, chainId, assetId, tokenPriceId, fresh.redeemable, fresh.unbonding, fresh.bonded)
        }
        check(updated == 1) { "Fresh asset update did not affect exactly one identity" }
    }

    /** Metadata-only write: an existing row's price id and all numeric storage remain untouched. */
    @Transaction
    open suspend fun markAssetNotNeed(
        metaId: Long, accountId: AccountId, chainId: String, assetId: String, initialTokenPriceId: String?
    ) {
        val stableAccountId = accountId.copyOf()
        val updated = updateMarkedNotNeed(metaId, stableAccountId, chainId, assetId)
        check(updated in 0..1) { "Metadata update affected multiple asset identities" }
        if (updated == 1) return
        insertAssets(listOf(AssetLocal.createEmpty(stableAccountId, assetId, chainId, metaId, initialTokenPriceId, enabled = null)))
        check(updateMarkedNotNeed(metaId, stableAccountId, chainId, assetId) == 1) {
            "Metadata update did not affect exactly one identity"
        }
    }

    @Query(FreshAssetWriteSql.MOVE_EMPTY_IF_ABSENT)
    protected abstract suspend fun moveEmptyAccountIfTargetAbsent(metaId: Long, accountId: AccountId, chainId: String, assetId: String, emptyAccountId: AccountId): Int

    @Query(FreshAssetWriteSql.FREE)
    protected abstract suspend fun updateFreshFree(metaId: Long, accountId: AccountId, chainId: String, assetId: String, tokenPriceId: String?, free: BigInteger?): Int

    @Query(FreshAssetWriteSql.FREE_AND_STATUS)
    protected abstract suspend fun updateFreshFreeAndStatus(metaId: Long, accountId: AccountId, chainId: String, assetId: String, tokenPriceId: String?, free: BigInteger?, status: String?): Int

    @Query(FreshAssetWriteSql.ACCOUNT_INFO)
    protected abstract suspend fun updateFreshAccountInfo(metaId: Long, accountId: AccountId, chainId: String, assetId: String, tokenPriceId: String?, free: BigInteger?, reserved: BigInteger?, miscFrozen: BigInteger?, feeFrozen: BigInteger?): Int

    @Query(FreshAssetWriteSql.ORML)
    protected abstract suspend fun updateFreshOrml(metaId: Long, accountId: AccountId, chainId: String, assetId: String, tokenPriceId: String?, free: BigInteger?, reserved: BigInteger?, miscFrozen: BigInteger?): Int

    @Query(FreshAssetWriteSql.STAKING)
    protected abstract suspend fun updateFreshStaking(metaId: Long, accountId: AccountId, chainId: String, assetId: String, tokenPriceId: String?, redeemable: BigInteger?, unbonding: BigInteger?, bonded: BigInteger?): Int

    @Query(FreshAssetWriteSql.MARK_NOT_NEED)
    protected abstract suspend fun updateMarkedNotNeed(metaId: Long, accountId: AccountId, chainId: String, assetId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAsset(asset: AssetLocal)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAssets(assets: List<AssetLocal>)

    @Update(entity = AssetLocal::class)
    abstract suspend fun updateAssets(item: List<AssetUpdateItem>): Int

    @Update(entity = AssetLocal::class)
    abstract suspend fun updateAsset(asset: AssetLocal)

    @Update(entity = AssetLocal::class)
    abstract suspend fun updateAsset(asset: AssetBalanceUpdateItem): Int

    @Upsert(entity = AssetLocal::class)
    abstract suspend fun updateOrInsertAsset(asset: AssetLocal)

    /**
     * Applies a scan result without replacing the user's explicit visibility choice.
     * A new row remains in the nullable `auto` state; later scans preserve explicit shown/hidden
     * preferences and update balance fields only.
     */
    @Transaction
    open suspend fun updateBalanceOrInsertPreservingPreference(
        balance: AssetBalanceUpdateItem,
        tokenPriceId: String?
    ) {
        if (updateAsset(balance) > 0) return

        insertAssets(
            listOf(
                AssetLocal.createEmpty(
                    accountId = balance.accountId,
                    id = balance.id,
                    chainId = balance.chainId,
                    metaId = balance.metaId,
                    tokenPriceId = tokenPriceId,
                    enabled = null
                ).copy(
                    freeInPlanks = balance.freeInPlanks,
                    reservedInPlanks = balance.reservedInPlanks,
                    miscFrozenInPlanks = balance.miscFrozenInPlanks,
                    feeFrozenInPlanks = balance.feeFrozenInPlanks,
                    status = balance.status
                )
            )
        )

        // Handles a concurrent insert while retaining whichever preference won the race.
        updateAsset(balance)
    }

    /** Creates preference tombstones even before an asset has a balance row. */
    @Transaction
    open suspend fun updateOrInsertAssetPreferences(items: List<AssetUpdateItem>) {
        items.forEach { item ->
            if (updateAssets(listOf(item)) == 0) {
                insertAssets(
                    listOf(
                        AssetLocal.createEmpty(
                            accountId = item.accountId,
                            id = item.id,
                            chainId = item.chainId,
                            metaId = item.metaId,
                            tokenPriceId = item.tokenPriceId,
                            enabled = item.enabled
                        ).copy(sortIndex = item.sortIndex)
                    )
                )
                updateAssets(listOf(item))
            }
        }
    }

    @Query("DELETE FROM assets WHERE metaId = :metaId AND accountId = :accountId AND chainId = :chainId AND id = :assetId")
    abstract fun deleteAsset(metaId: Long, accountId: AccountId, chainId: String, assetId: String)

    @Query("DELETE FROM assets WHERE id in (:assetIdsToDelete)")
    abstract fun deleteAssets(assetIdsToDelete: List<String>)

    @Query("DELETE FROM assets WHERE metaId = :metaId")
    abstract fun deleteAccountAssets(metaId: Long)

    /**
     * Intermediate-detail lookup scoped to the exact database identity. Symbols are display
     * metadata and registry asset ids are not globally unique, so both chain and asset id are
     * mandatory. This changes no released table or migration schema.
     */
    suspend fun getAssets(
        accountMetaId: Long,
        chainId: String,
        id: String
    ): List<CachedAssetWithPriceRead> = getChainAssetRows(accountMetaId, chainId, id).map { it.decode() }

    @Transaction
    @Query(CachedAssetProjectionSql.EXACT_CHAIN_ASSET)
    protected abstract suspend fun getChainAssetRows(
        accountMetaId: Long,
        chainId: String,
        id: String
    ): List<RawCachedAssetWithPriceRow>

    /** See [getAssets]. */
    fun observeAssets(
        accountMetaId: Long,
        chainId: String,
        id: String
    ): Flow<List<CachedAssetWithPriceRead>> =
        observeChainAssetRows(accountMetaId, chainId, id).map { rows -> rows.map { it.decode() } }

    @Transaction
    @Query(CachedAssetProjectionSql.EXACT_CHAIN_ASSET)
    protected abstract fun observeChainAssetRows(
        accountMetaId: Long,
        chainId: String,
        id: String
    ): Flow<List<RawCachedAssetWithPriceRow>>

    companion object {
        const val xcPrefix = "xc"
    }
}
