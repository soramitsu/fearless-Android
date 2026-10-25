package jp.co.soramitsu.coredb.dao

import jp.co.soramitsu.coredb.model.*
import jp.co.soramitsu.coredb.model.cache.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import java.math.BigInteger

class AssetReadProjectionDaoTest {
    private val account = byteArrayOf(7, 8)
    private fun cell(value: String?): CachedNumericCell = value?.toByteArray()?.let {
        CachedNumericCell("text", it.size.toLong(), it)
    } ?: CachedNumericCell("null", 0, null)

    private fun row(id: String, free: String?, accountId: ByteArray = account): RawCachedAssetWithPriceRow =
        RawCachedAssetWithPriceRow(
            RawCachedAssetRow(id, "chain", accountId, 7, "price", 3, true, false, "name", "status",
                cell(free), cell("11"), cell(null), cell("2"), cell("5"), cell(null), cell("7")),
            "price", "USD", cell("2.75"), cell("invalid-price")
        )

    private val mixed get() = listOf(row("healthy", "123456789012345678901234567890"), row("damaged", "1broken"))

    private fun assertMixed(rows: List<CachedAssetWithPriceRead>) {
        assertEquals(listOf("healthy", "damaged"), rows.map { it.asset.id })
        assertEquals(CachedNumericState.Known(BigInteger("123456789012345678901234567890")), rows[0].asset.free)
        assertTrue(rows[1].asset.free is CachedNumericState.Unavailable)
        rows.forEach {
            assertEquals(CachedNumericState.Known(BigInteger("11")), it.asset.reserved)
            assertSame(CachedNumericState.Missing, it.asset.miscFrozen)
            assertEquals("name", it.asset.chainAccountName)
            assertTrue(it.asset.enabled == true)
            assertTrue(it.token!!.recentRateChange is CachedNumericState.Unavailable)
            assertEquals("2.75", (it.token!!.fiatRate as CachedNumericState.Known).value.toPlainString())
        }
    }

    @Test fun directBalancesKeepHealthyAndDamagedRowsWithoutAnyWrite() = runBlocking {
        val dao = Fake(mixed)
        val rows = dao.observeBalances(7).first()
        assertEquals(2, rows.size)
        assertEquals(mixed[0].asset.decode().free, rows[0].free)
        assertTrue(rows[1].free is CachedNumericState.Unavailable)
        assertEquals(listOf("balances:7"), dao.reads)
    }

    @Test fun accountObservationDoesNotDropUnknownAssetsOrUnknownPrices() = runBlocking {
        val dao = Fake(mixed)
        assertMixed(dao.observeAssets(7).first())
        assertEquals(listOf("observeAccount:7"), dao.reads)
    }

    @Test fun accountSnapshotKeepsIdenticalAmountsAndAvailability() = runBlocking {
        val dao = Fake(mixed)
        assertMixed(dao.getAssets(7))
        assertEquals(listOf("getAccount:7"), dao.reads)
    }

    @Test fun singleObservationSkipsAbsentRowThenPreservesAllFallbackStates() = runBlocking {
        val raw = row("damaged", "broken", byteArrayOf())
        val dao = Fake(listOf(raw))
        dao.singleEmissions = listOf(null, raw)
        val result = dao.observeAsset(7, account, "chain", "damaged").toList().single()
        assertArrayEquals(account, result.asset.accountId)
        assertEquals(raw.decode().asset.free, result.asset.free)
        assertEquals(raw.decode().asset.bonded, result.asset.bonded)
        assertSame(CachedNumericState.Missing, result.asset.redeemable)
        assertEquals(raw.decode().token, result.token)
        assertEquals("observeSingle:7:chain:damaged", dao.reads.single())
        assertArrayEquals(byteArrayOf(), dao.lastEmptyAccount)
        account[0] = 99
        assertArrayEquals(byteArrayOf(7, 8), result.asset.accountId)
    }

    @Test fun singleSnapshotPreservesDatabaseAccountIdentityAndUnknownValue() = runBlocking {
        val raw = row("damaged", "broken", byteArrayOf())
        val dao = Fake(listOf(raw))
        val result = dao.getAsset(7, account, "chain", "damaged")!!
        assertArrayEquals(byteArrayOf(), result.asset.accountId)
        assertTrue(result.asset.free is CachedNumericState.Unavailable)
        assertEquals(listOf("getSingle:7:chain:damaged"), dao.reads)
        assertArrayEquals(byteArrayOf(), dao.lastEmptyAccount)
    }

    @Test fun missingSingleSnapshotRemainsAbsent() = runBlocking {
        val dao = Fake(emptyList())
        assertNull(dao.getAsset(7, account, "chain", "missing"))
    }

    @Test fun symbolSnapshotPreservesSiblingAmountsAndUnknownRows() = runBlocking {
        val dao = Fake(mixed)
        assertMixed(dao.getAssets(7, account, "chain", "TOKEN"))
        assertEquals(listOf("symbol:7:chain:TOKEN"), dao.reads)
        assertArrayEquals(byteArrayOf(), dao.lastEmptyAccount)
    }

    @Test fun exactChainSnapshotPreservesIdentityAndAvailability() = runBlocking {
        val dao = Fake(mixed)
        assertMixed(dao.getAssets(7, "specific-chain", "same-id"))
        assertEquals(listOf("getChain:7:specific-chain:same-id"), dao.reads)
    }

    @Test fun exactChainObservationPreservesIdentityAndAvailability() = runBlocking {
        val dao = Fake(mixed)
        assertMixed(dao.observeAssets(7, "specific-chain", "same-id").first())
        assertEquals(listOf("observeChain:7:specific-chain:same-id"), dao.reads)
    }

    @Test fun laterRefreshCanRecoverOneRowWithoutTerminatingListObservation() = runBlocking {
        val dao = Fake(mixed)
        dao.accountEmissions = listOf(mixed, listOf(mixed.first(), row("damaged", "42")))
        val results = dao.observeAssets(7).toList()
        assertMixed(results.first())
        assertEquals(CachedNumericState.Known(BigInteger("42")), results.last()[1].asset.free)
        assertEquals(results.first()[0].asset.free, results.last()[0].asset.free)
    }

    @Test fun priceAbsenceRemainsDistinctFromMalformedPrice() = runBlocking {
        val raw = row("missing-price", "42").copy(priceId = null, fiatSymbol = null, fiatRate = cell(null), recentRateChange = cell(null))
        val dao = Fake(listOf(raw))
        assertNull(dao.getAssets(7).single().token)
        assertEquals(CachedNumericState.Known(BigInteger("42")), dao.getAssets(7).single().asset.free)
    }

    @Test fun missingAndLegacyErrorFreeRemainDistinctAtReadBoundary() = runBlocking {
        val rows = Fake(listOf(row("loading", null), row("error", "-1"))).getAssets(7)
        assertSame(CachedNumericState.Missing, rows[0].asset.free)
        assertEquals(CachedNumericState.Known(BigInteger("-1")), rows[1].asset.free)
    }

    /** Any accidental read-to-write path fails immediately; this is not a Room substitute. */
    open class Fake(val rows: List<RawCachedAssetWithPriceRow>) : AssetDao() {
        val reads = mutableListOf<String>()
        var lastEmptyAccount: ByteArray? = null
        var singleEmissions: List<RawCachedAssetWithPriceRow?> = listOf(rows.firstOrNull())
        var accountEmissions = listOf(rows)
        override fun observePortfolioBalanceRows(metaId: Long): Flow<List<RawCachedAssetRow>> { reads += "observePortfolioBalances:$metaId"; return accountEmissions.map { list -> list.map { it.asset } }.asFlow() }
        override suspend fun getPortfolioBalanceRows(metaId: Long): List<RawCachedAssetRow> { reads += "getPortfolioBalances:$metaId"; return rows.map { it.asset } }
        override fun observePortfolioAssetRows(metaId: Long): Flow<List<RawCachedAssetWithPriceRow>> { reads += "observePortfolio:$metaId"; return accountEmissions.asFlow() }
        override suspend fun getPortfolioAssetRows(metaId: Long): List<RawCachedAssetWithPriceRow> { reads += "getPortfolio:$metaId"; return rows }
        override fun observeBalanceRows(metaId: Long): Flow<List<RawCachedAssetRow>> { reads += "balances:$metaId"; return flowOf(rows.map { it.asset }) }
        override fun observeAccountAssetRows(metaId: Long): Flow<List<RawCachedAssetWithPriceRow>> { reads += "observeAccount:$metaId"; return accountEmissions.asFlow() }
        override suspend fun getAccountAssetRows(metaId: Long): List<RawCachedAssetWithPriceRow> { reads += "getAccount:$metaId"; return rows }
        override fun observeAssetWithEmpty(metaId: Long, accountId: ByteArray, chainId: String, assetId: String, emptyAccountId: ByteArray): Flow<RawCachedAssetWithPriceRow?> { reads += "observeSingle:$metaId:$chainId:$assetId"; lastEmptyAccount=emptyAccountId; return singleEmissions.asFlow() }
        override suspend fun getAssetWithEmpty(metaId: Long, accountId: ByteArray, chainId: String, assetId: String, emptyAccountId: ByteArray): RawCachedAssetWithPriceRow? { reads += "getSingle:$metaId:$chainId:$assetId"; lastEmptyAccount=emptyAccountId; return rows.firstOrNull() }
        override suspend fun getAssetsWithEmpty(metaId: Long, accountId: ByteArray, chainId: String, symbol: String, emptyAccountId: ByteArray): List<RawCachedAssetWithPriceRow> { reads += "symbol:$metaId:$chainId:$symbol"; lastEmptyAccount=emptyAccountId; return rows }
        override suspend fun getChainAssetRows(accountMetaId: Long, chainId: String, id: String): List<RawCachedAssetWithPriceRow> { reads += "getChain:$accountMetaId:$chainId:$id"; return rows }
        override fun observeChainAssetRows(accountMetaId: Long, chainId: String, id: String): Flow<List<RawCachedAssetWithPriceRow>> { reads += "observeChain:$accountMetaId:$chainId:$id"; return flowOf(rows) }
        override fun observeChainIdsWithShownOrFundedAssets(): Flow<List<String>> = error("not exercised")
        override suspend fun getExplicitAssetPresentation(metaId: Long): List<AssetPresentationLocal> = error("not exercised")
        override suspend fun insertAsset(asset: AssetLocal): Unit = error("read called writer")
        override suspend fun insertAssets(assets: List<AssetLocal>): Unit = error("read called writer")
        override suspend fun updateAssets(item: List<AssetUpdateItem>): Int = error("read called writer")
        override suspend fun updateAsset(asset: AssetLocal): Unit = error("read called writer")
        override suspend fun updateAsset(asset: AssetBalanceUpdateItem): Int = error("read called writer")
        override suspend fun updateOrInsertAsset(asset: AssetLocal): Unit = error("read called writer")
        override fun deleteAsset(metaId: Long, accountId: ByteArray, chainId: String, assetId: String): Unit = error("read called writer")
        override fun deleteAssets(assetIdsToDelete: List<String>): Unit = error("read called writer")
        override suspend fun moveEmptyAccountIfTargetAbsent(metaId: Long, accountId: AccountId, chainId: String, assetId: String, emptyAccountId: AccountId): Int = error("read called fresh writer")
        override suspend fun updateFreshFree(metaId: Long, accountId: AccountId, chainId: String, assetId: String, tokenPriceId: String?, free: BigInteger?): Int = error("read called fresh writer")
        override suspend fun updateFreshFreeAndStatus(metaId: Long, accountId: AccountId, chainId: String, assetId: String, tokenPriceId: String?, free: BigInteger?, status: String?): Int = error("read called fresh writer")
        override suspend fun updateFreshAccountInfo(metaId: Long, accountId: AccountId, chainId: String, assetId: String, tokenPriceId: String?, free: BigInteger?, reserved: BigInteger?, miscFrozen: BigInteger?, feeFrozen: BigInteger?): Int = error("read called fresh writer")
        override suspend fun updateFreshOrml(metaId: Long, accountId: AccountId, chainId: String, assetId: String, tokenPriceId: String?, free: BigInteger?, reserved: BigInteger?, miscFrozen: BigInteger?): Int = error("read called fresh writer")
        override suspend fun updateFreshStaking(metaId: Long, accountId: AccountId, chainId: String, assetId: String, tokenPriceId: String?, redeemable: BigInteger?, unbonding: BigInteger?, bonded: BigInteger?): Int = error("read called fresh writer")
        override suspend fun updateMarkedNotNeed(metaId: Long, accountId: AccountId, chainId: String, assetId: String): Int = error("read called fresh writer")
        override fun deleteAccountAssets(metaId: Long): Unit = error("read called writer")
    }
}
