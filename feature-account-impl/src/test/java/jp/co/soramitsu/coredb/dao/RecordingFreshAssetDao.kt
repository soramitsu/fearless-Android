package jp.co.soramitsu.coredb.dao

import jp.co.soramitsu.coredb.model.*
import jp.co.soramitsu.coredb.model.cache.*
import jp.co.soramitsu.coredb.converters.LongMathConverters
import kotlinx.coroutines.flow.Flow
import java.math.BigInteger
import java.util.Base64

/** Records actual candidate DAO application. It does not emulate Room transactions. */
class RecordingFreshAssetDao : AssetDao() {
    override fun observePortfolioAssetRows(metaId: Long): Flow<List<RawCachedAssetWithPriceRow>> = error("Unexpected numeric read")
    override suspend fun getPortfolioAssetRows(metaId: Long): List<RawCachedAssetWithPriceRow> = error("Unexpected numeric read")
    override fun observePortfolioBalanceRows(metaId: Long): Flow<List<RawCachedAssetRow>> = error("Unexpected numeric read")
    override suspend fun getPortfolioBalanceRows(metaId: Long): List<RawCachedAssetRow> = error("Unexpected numeric read")
    data class Operation(val kind: String, val sql: String?, val params: Map<String, Any?>)
    val operations = mutableListOf<Operation>()
    var freshResult = 1
    val markResults = ArrayDeque(listOf(1))
    var onMove: (() -> Unit)? = null
    var freshFailure: Throwable? = null
    private val converter = LongMathConverters()
    private fun encoded(value: Any?): Any? = when (value) {
        is ByteArray -> mapOf("blob" to Base64.getEncoder().encodeToString(value))
        is BigInteger -> converter.fromBigInteger(value)
        is Boolean -> if (value) 1 else 0
        else -> value
    }
    private fun record(kind: String, sql: String?, values: Map<String, Any?>): Int {
        operations += Operation(kind, sql, values.mapValues { encoded(it.value) })
        if (kind == "move") onMove?.invoke()
        if (kind == "fresh") freshFailure?.let { throw it }
        return if (kind == "mark") markResults.removeFirst() else freshResult
    }
    private fun identity(metaId: Long, accountId: ByteArray, chainId: String, assetId: String) =
        mapOf<String, Any?>("metaId" to metaId, "accountId" to accountId, "chainId" to chainId, "assetId" to assetId)

    override suspend fun insertAssets(assets: List<AssetLocal>) {
        assets.forEach { a ->
            record("insert-ignore", null, mapOf("id" to a.id, "chainId" to a.chainId, "accountId" to a.accountId,
                "metaId" to a.metaId, "tokenPriceId" to a.tokenPriceId, "freeInPlanks" to a.freeInPlanks,
                "reservedInPlanks" to a.reservedInPlanks, "miscFrozenInPlanks" to a.miscFrozenInPlanks,
                "feeFrozenInPlanks" to a.feeFrozenInPlanks, "bondedInPlanks" to a.bondedInPlanks,
                "redeemableInPlanks" to a.redeemableInPlanks, "unbondingInPlanks" to a.unbondingInPlanks,
                "sortIndex" to a.sortIndex, "enabled" to a.enabled, "markedNotNeed" to a.markedNotNeed,
                "chainAccountName" to a.chainAccountName, "status" to a.status))
        }
    }
    override suspend fun moveEmptyAccountIfTargetAbsent(metaId: Long, accountId: ByteArray, chainId: String, assetId: String, emptyAccountId: ByteArray): Int =
        record("move", FreshAssetWriteSql.MOVE_EMPTY_IF_ABSENT, identity(metaId, accountId, chainId, assetId) + ("emptyAccountId" to emptyAccountId))
    override suspend fun updateFreshFree(metaId: Long, accountId: ByteArray, chainId: String, assetId: String, tokenPriceId: String?, free: BigInteger?): Int =
        record("fresh", FreshAssetWriteSql.FREE, identity(metaId, accountId, chainId, assetId) + mapOf("tokenPriceId" to tokenPriceId, "free" to free))
    override suspend fun updateFreshFreeAndStatus(metaId: Long, accountId: ByteArray, chainId: String, assetId: String, tokenPriceId: String?, free: BigInteger?, status: String?): Int =
        record("fresh", FreshAssetWriteSql.FREE_AND_STATUS, identity(metaId, accountId, chainId, assetId) + mapOf("tokenPriceId" to tokenPriceId, "free" to free, "status" to status))
    override suspend fun updateFreshAccountInfo(metaId: Long, accountId: ByteArray, chainId: String, assetId: String, tokenPriceId: String?, free: BigInteger?, reserved: BigInteger?, miscFrozen: BigInteger?, feeFrozen: BigInteger?): Int =
        record("fresh", FreshAssetWriteSql.ACCOUNT_INFO, identity(metaId, accountId, chainId, assetId) + mapOf("tokenPriceId" to tokenPriceId, "free" to free, "reserved" to reserved, "miscFrozen" to miscFrozen, "feeFrozen" to feeFrozen))
    override suspend fun updateFreshOrml(metaId: Long, accountId: ByteArray, chainId: String, assetId: String, tokenPriceId: String?, free: BigInteger?, reserved: BigInteger?, miscFrozen: BigInteger?): Int =
        record("fresh", FreshAssetWriteSql.ORML, identity(metaId, accountId, chainId, assetId) + mapOf("tokenPriceId" to tokenPriceId, "free" to free, "reserved" to reserved, "miscFrozen" to miscFrozen))
    override suspend fun updateFreshStaking(metaId: Long, accountId: ByteArray, chainId: String, assetId: String, tokenPriceId: String?, redeemable: BigInteger?, unbonding: BigInteger?, bonded: BigInteger?): Int =
        record("fresh", FreshAssetWriteSql.STAKING, identity(metaId, accountId, chainId, assetId) + mapOf("tokenPriceId" to tokenPriceId, "redeemable" to redeemable, "unbonding" to unbonding, "bonded" to bonded))
    override suspend fun updateMarkedNotNeed(metaId: Long, accountId: ByteArray, chainId: String, assetId: String): Int =
        record("mark", FreshAssetWriteSql.MARK_NOT_NEED, identity(metaId, accountId, chainId, assetId))
    override fun observeBalanceRows(metaId: Long): Flow<List<RawCachedAssetRow>> = error("writer attempted cached read or full-row write")
    override fun observeAccountAssetRows(metaId: Long): Flow<List<RawCachedAssetWithPriceRow>> = error("writer attempted cached read or full-row write")
    override suspend fun getAccountAssetRows(metaId: Long): List<RawCachedAssetWithPriceRow> = error("writer attempted cached read or full-row write")
    override fun observeAssetWithEmpty(metaId: Long, accountId: ByteArray, chainId: String, assetId: String, emptyAccountId: ByteArray): Flow<RawCachedAssetWithPriceRow?> = error("writer attempted cached read or full-row write")
    override suspend fun getAssetWithEmpty(metaId: Long, accountId: ByteArray, chainId: String, assetId: String, emptyAccountId: ByteArray): RawCachedAssetWithPriceRow? = error("writer attempted cached read or full-row write")
    override suspend fun getAssetsWithEmpty(metaId: Long, accountId: ByteArray, chainId: String, symbol: String, emptyAccountId: ByteArray): List<RawCachedAssetWithPriceRow> = error("writer attempted cached read or full-row write")
    override suspend fun getChainAssetRows(accountMetaId: Long, chainId: String, id: String): List<RawCachedAssetWithPriceRow> = error("writer attempted cached read or full-row write")
    override fun observeChainAssetRows(accountMetaId: Long, chainId: String, id: String): Flow<List<RawCachedAssetWithPriceRow>> = error("writer attempted cached read or full-row write")
    override fun observeChainIdsWithShownOrFundedAssets(): Flow<List<String>> = error("writer attempted cached read or full-row write")
    override suspend fun getExplicitAssetPresentation(metaId: Long): List<AssetPresentationLocal> = error("writer attempted cached read or full-row write")
    override suspend fun insertAsset(asset: AssetLocal): Unit = error("writer attempted cached read or full-row write")
    override suspend fun updateAssets(item: List<AssetUpdateItem>): Int = error("writer attempted cached read or full-row write")
    override suspend fun updateAsset(asset: AssetLocal): Unit = error("writer attempted cached read or full-row write")
    override suspend fun updateAsset(asset: AssetBalanceUpdateItem): Int = error("writer attempted cached read or full-row write")
    override suspend fun updateOrInsertAsset(asset: AssetLocal): Unit = error("writer attempted cached read or full-row write")
    override fun deleteAsset(metaId: Long, accountId: ByteArray, chainId: String, assetId: String): Unit = error("writer attempted cached read or full-row write")
    override fun deleteAssets(assetIdsToDelete: List<String>): Unit = error("writer attempted cached read or full-row write")
    override fun deleteAccountAssets(metaId: Long): Unit = error("writer attempted cached read or full-row write")
}
