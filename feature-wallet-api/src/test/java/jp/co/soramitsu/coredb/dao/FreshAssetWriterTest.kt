package jp.co.soramitsu.coredb.dao

import com.google.gson.GsonBuilder
import io.mockk.*
import jp.co.soramitsu.common.data.network.runtime.binding.*
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.model.cache.*
import jp.co.soramitsu.wallet.api.data.cache.AssetCache
import jp.co.soramitsu.wallet.api.data.cache.updateAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

object FreshWriterFixture {
    val account get() = byteArrayOf(7, 8)
    fun asset(provider: Boolean = false): Asset = mockk {
        every { chainId } returns "chain"
        every { id } returns "asset"
        every { currency } returns BigInteger("42")
        every { priceId } returns "new-price"
        every { priceProvider } returns if (provider) mockk {
            every { type } returns Asset.PriceProviderType.Chainlink
            every { id } returns "chainlink-price"
        } else null
    }
    fun cache(dao: RecordingFreshAssetDao, usd: Boolean = true): AssetCache {
        val prefs = mockk<Preferences> { every { getString(any(), any()) } returns if (usd) "usd" else "eur" }
        return AssetCache(dao, SelectedFiat(prefs), Dispatchers.Unconfined)
    }
    suspend fun fromProvider(data: AssetBalanceData?, asset: Asset = asset(), usd: Boolean = true): RecordingFreshAssetDao =
        RecordingFreshAssetDao().also { cache(it, usd).updateAsset(7, account, asset, data) }
    fun variants(): Map<String, AssetBalanceData?> = linkedMapOf(
        "empty-null" to null,
        "empty-object" to EmptyBalance,
        "simple" to SimpleBalanceData(BigInteger("123456789012345678901234567890")),
        "account-info" to AccountInfo(BigInteger.ZERO, AccountData(BigInteger("101"), BigInteger("102"), BigInteger("103"), BigInteger("104"))),
        "orml" to OrmlTokensAccountData(BigInteger("201"), BigInteger("202"), BigInteger("203")),
        "equilibrium" to EqAccountInfo(BigInteger.ZERO, EqAccountData(BigInteger("999"), mapOf(BigInteger("42") to BigInteger("301")))),
        "equilibrium-absent" to EqAccountInfo(BigInteger.ZERO, EqAccountData(BigInteger("999"), emptyMap())),
        "assets-status" to AssetsAccountInfo(BigInteger("401"), "Frozen"),
        "assets-null-status" to AssetsAccountInfo(BigInteger("402"), null)
    )
    suspend fun traces(): Map<String, List<RecordingFreshAssetDao.Operation>> {
        val traces = linkedMapOf<String, List<RecordingFreshAssetDao.Operation>>()
        variants().forEach { (name, data) -> traces[name] = fromProvider(data).operations }
        val payloads = linkedMapOf<String, FreshAssetCacheUpdate>(
            "staking" to FreshAssetCacheUpdate.Staking(BigInteger("501"), BigInteger("502"), BigInteger("503")),
            "staking-empty" to FreshAssetCacheUpdate.Staking(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO),
            "explicit-null-free" to FreshAssetCacheUpdate.Free(null)
        )
        payloads.forEach { (name, payload) ->
            val dao = RecordingFreshAssetDao()
            cache(dao).updateAsset(7, account, asset(), payload)
            traces[name] = dao.operations
        }
        val direct = RecordingFreshAssetDao()
        direct.updateFreshAssetCache(7, account, "chain", "asset", null, FreshAssetCacheUpdate.Free(BigInteger("601")))
        traces["explicit-null-price"] = direct.operations
        val empty = RecordingFreshAssetDao()
        empty.updateFreshAssetCache(7, byteArrayOf(), "chain", "asset", "new-price", FreshAssetCacheUpdate.Free(BigInteger("701")))
        traces["empty-account"] = empty.operations
        for (missing in listOf(false, true)) {
            val dao = RecordingFreshAssetDao()
            if (missing) { dao.markResults.clear(); dao.markResults.addAll(listOf(0, 1)) }
            dao.markAssetNotNeed(7, byteArrayOf(), "chain", "asset", "new-price")
            traces[if (missing) "mark-missing" else "mark-existing"] = dao.operations
        }
        return traces
    }
}

class FreshAssetWriterTest {
    private suspend fun fields(data: AssetBalanceData?): Map<String, Any?> = FreshWriterFixture.fromProvider(data).operations.last().params - setOf("metaId", "accountId", "chainId", "assetId", "tokenPriceId")
    @Test fun nullProviderResultWritesOnlyFreeZero() = runBlocking { assertEquals(mapOf("free" to "0"), fields(null)) }
    @Test fun emptyProviderResultWritesOnlyFreeZero() = runBlocking { assertEquals(mapOf("free" to "0"), fields(EmptyBalance)) }
    @Test fun simpleProviderKeepsExactLargeAmount() = runBlocking { assertEquals(mapOf("free" to "123456789012345678901234567890"), fields(FreshWriterFixture.variants().getValue("simple"))) }
    @Test fun accountInfoSuppliesExactlyFourFreshFields() = runBlocking { assertEquals(mapOf("free" to "101", "reserved" to "102", "miscFrozen" to "103", "feeFrozen" to "104"), fields(FreshWriterFixture.variants().getValue("account-info"))) }
    @Test fun ormlOmitsFeeFrozenAndEveryStakingField() = runBlocking { assertEquals(mapOf("free" to "201", "reserved" to "202", "miscFrozen" to "203"), fields(FreshWriterFixture.variants().getValue("orml"))) }
    @Test fun equilibriumUpdatesOnlySelectedCurrencyFree() = runBlocking { assertEquals(mapOf("free" to "301"), fields(FreshWriterFixture.variants().getValue("equilibrium"))) }
    @Test fun equilibriumMissingCurrencyRetainsPriorZeroPolicy() = runBlocking { assertEquals(mapOf("free" to "0"), fields(FreshWriterFixture.variants().getValue("equilibrium-absent"))) }
    @Test fun assetsAccountInfoSuppliesFreeAndStatusOnly() = runBlocking { assertEquals(mapOf("free" to "401", "status" to "Frozen"), fields(FreshWriterFixture.variants().getValue("assets-status"))) }
    @Test fun explicitNullStatusIsFreshRatherThanOmitted() = runBlocking { assertEquals(mapOf("free" to "402", "status" to null), fields(FreshWriterFixture.variants().getValue("assets-null-status"))) }
    @Test fun unknownProviderTypeDoesNotReadOrWrite() = runBlocking { assertTrue(FreshWriterFixture.fromProvider(object : AssetBalanceData {}).operations.isEmpty()) }
    @Test fun stakingSuppliesExactlyThreeFields() = runBlocking {
        val fresh = FreshWriterFixture.traces().getValue("staking").last()
        assertEquals(FreshAssetWriteSql.STAKING, fresh.sql)
        assertEquals(setOf("metaId", "accountId", "chainId", "assetId", "tokenPriceId", "redeemable", "unbonding", "bonded"), fresh.params.keys)
        assertEquals(listOf("501", "502", "503"), listOf(fresh.params["redeemable"], fresh.params["unbonding"], fresh.params["bonded"]))
    }
    @Test fun stakingEmptyLedgerDoesNotZeroNonStakingFields() = runBlocking {
        val fresh = FreshWriterFixture.traces().getValue("staking-empty").last()
        assertEquals(FreshAssetWriteSql.STAKING, fresh.sql)
        assertEquals(listOf("0", "0", "0"), listOf(fresh.params["redeemable"], fresh.params["unbonding"], fresh.params["bonded"]))
    }
    @Test fun cacheUsesChainlinkPriceOnlyForUsd() = runBlocking {
        assertEquals("chainlink-price", FreshWriterFixture.fromProvider(SimpleBalanceData(BigInteger.ONE), FreshWriterFixture.asset(true)).operations.last().params["tokenPriceId"])
        assertEquals("new-price", FreshWriterFixture.fromProvider(SimpleBalanceData(BigInteger.ONE), FreshWriterFixture.asset(true), false).operations.last().params["tokenPriceId"])
    }
    @Test fun absentProviderUsesCatalogPrice() = runBlocking { assertEquals("new-price", FreshWriterFixture.fromProvider(SimpleBalanceData(BigInteger.ONE)).operations.last().params["tokenPriceId"]) }
    @Test fun existingAndNewRowsUseMoveIgnoreThenFreshApplication() = runBlocking {
        assertEquals(listOf("move", "insert-ignore", "fresh"), FreshWriterFixture.fromProvider(SimpleBalanceData(BigInteger.ONE)).operations.map { it.kind })
    }
    @Test fun newRowDefaultsAreAutoAndDoNotEmbedFreshFields() = runBlocking {
        val blank = FreshWriterFixture.fromProvider(SimpleBalanceData(BigInteger.ONE)).operations[1].params
        assertNull(blank["enabled"]); assertNull(blank["freeInPlanks"])
        assertEquals(Int.MAX_VALUE, blank["sortIndex"]); assertEquals(0, blank["markedNotNeed"])
    }
    @Test fun emptyAccountDoesNotMoveItself() = runBlocking { assertEquals(listOf("insert-ignore", "fresh"), FreshWriterFixture.traces().getValue("empty-account").map { it.kind }) }
    @Test fun accountIdentityIsCopiedBeforeFirstSuspension() = runBlocking {
        val account = FreshWriterFixture.account; val dao = RecordingFreshAssetDao(); dao.onMove = { account[0] = 99 }
        dao.updateFreshAssetCache(7, account, "chain", "asset", "price", FreshAssetCacheUpdate.Free(BigInteger.ONE))
        val identities = dao.operations.map { it.params.getValue("accountId") }
        assertEquals(1, identities.distinct().size); assertEquals(mapOf("blob" to "Bwg="), identities.first())
    }
    @Test fun absentFreshUpdateDoesNotReportSuccess() = runBlocking {
        val dao = RecordingFreshAssetDao(); dao.freshResult = 0
        try { dao.updateFreshAssetCache(7, FreshWriterFixture.account, "chain", "asset", null, FreshAssetCacheUpdate.Free(BigInteger.ONE)); fail() } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("exactly one")) }
    }
    @Test fun multiRowFreshUpdateDoesNotReportSuccess() = runBlocking {
        val dao = RecordingFreshAssetDao(); dao.freshResult = 2
        try { dao.updateFreshAssetCache(7, FreshWriterFixture.account, "chain", "asset", null, FreshAssetCacheUpdate.Free(BigInteger.ONE)); fail() } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("exactly one")) }
    }
    @Test fun cancellationPropagatesWithoutRetryOrFallbackWrite() = runBlocking {
        val dao = RecordingFreshAssetDao(); val failure = CancellationException("stop"); dao.freshFailure = failure
        try { dao.updateFreshAssetCache(7, FreshWriterFixture.account, "chain", "asset", null, FreshAssetCacheUpdate.Free(BigInteger.ONE)); fail() } catch (e: CancellationException) { assertSame(failure, e) }
        assertEquals(listOf("move", "insert-ignore", "fresh"), dao.operations.map { it.kind })
    }
    @Test fun databaseFailurePropagatesWithoutRetryOrFallbackWrite() = runBlocking {
        val dao = RecordingFreshAssetDao(); val failure = IllegalArgumentException("storage failure"); dao.freshFailure = failure
        try { dao.updateFreshAssetCache(7, FreshWriterFixture.account, "chain", "asset", null, FreshAssetCacheUpdate.Free(BigInteger.ONE)); fail() } catch (e: IllegalArgumentException) { assertSame(failure, e) }
        assertEquals(3, dao.operations.size)
    }
    @Test fun metadataExistingRowWritesOnlyMark() = runBlocking { assertEquals(listOf("mark"), FreshWriterFixture.traces().getValue("mark-existing").map { it.kind }) }
    @Test fun metadataMissingRowUsesIgnoreThenExactMark() = runBlocking { assertEquals(listOf("mark", "insert-ignore", "mark"), FreshWriterFixture.traces().getValue("mark-missing").map { it.kind }) }
    @Test fun metadataFailedInsertCannotReportSuccess() = runBlocking {
        val dao = RecordingFreshAssetDao(); dao.markResults.clear(); dao.markResults.addAll(listOf(0, 0))
        try { dao.markAssetNotNeed(7, byteArrayOf(), "chain", "asset", "price"); fail() } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("exactly one")) }
    }
    @Test fun metadataUnexpectedMultipleRowsFailBeforeInsert() = runBlocking {
        val dao = RecordingFreshAssetDao(); dao.markResults.clear(); dao.markResults.add(2)
        try { dao.markAssetNotNeed(7, byteArrayOf(), "chain", "asset", "price"); fail() } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("multiple")) }
        assertEquals(listOf("mark"), dao.operations.map { it.kind })
    }
}

object FreshWriterTraceMain {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        val output = mapOf("traces" to FreshWriterFixture.traces(), "singleAssetQuery" to CachedAssetProjectionSql.ACCOUNT_ASSET, "accountAssetsQuery" to CachedAssetProjectionSql.ACCOUNT_ASSETS, "symbolAssetsQuery" to CachedAssetProjectionSql.SYMBOL_ASSETS)
        println(GsonBuilder().serializeNulls().create().toJson(output))
    }
}
