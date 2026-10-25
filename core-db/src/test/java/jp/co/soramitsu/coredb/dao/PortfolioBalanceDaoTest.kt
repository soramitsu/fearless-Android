package jp.co.soramitsu.coredb.dao

import jp.co.soramitsu.coredb.model.cache.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class PortfolioBalanceDaoTest {
    private fun cell(value: String?) = value?.toByteArray()?.let { CachedNumericCell("text", it.size.toLong(), it) }
        ?: CachedNumericCell("null", 0, null)
    private fun row(id: String, value: String?, account: ByteArray, enabled: Boolean?) =
        RawCachedAssetWithPriceRow(RawCachedAssetRow(id, "chain", account, 7, "stale-price", 3, enabled, true,
            "account-name", "status", cell(value), cell("11"), cell(null), cell("2"), cell("5"), cell(null), cell("7")),
            "stale-price", "unused symbol", cell("1"), cell("bad"))

    @Test fun portfolioSnapshotUsesAssetOnlySeamAndPreservesEveryAccountAndPreference() = runBlocking {
        val raw = listOf(row("same", "100", byteArrayOf(1), true), row("same", "broken", byteArrayOf(), false),
            row("same", "300", byteArrayOf(2), null))
        val dao = AssetReadProjectionDaoTest.Fake(raw)
        val actual = dao.getPortfolioBalances(7)
        assertEquals(listOf("getPortfolioBalances:7"), dao.reads)
        assertEquals(3, actual.size)
        assertEquals(listOf(true, false, null), actual.map { it.enabled })
        assertEquals(listOf(listOf<Byte>(1), emptyList(), listOf<Byte>(2)), actual.map { it.accountId.toList() })
        assertTrue(actual[1].free is CachedNumericState.Unavailable)
        assertEquals(CachedNumericState.Known(BigInteger("100")), actual[0].free)
        actual.forEach { assertEquals("stale-price", it.tokenPriceId); assertTrue(it.markedNotNeed) }
    }

    @Test fun portfolioObservationPreservesEmptyRemovalAndRecovery() = runBlocking {
        val raw = row("asset", "broken", byteArrayOf(1), null)
        val dao = AssetReadProjectionDaoTest.Fake(listOf(raw))
        dao.accountEmissions = listOf(listOf(raw), emptyList(), listOf(raw.copy(asset=raw.asset.copy(free=cell("12")))))
        val values = dao.observePortfolioBalances(7).toList()
        assertEquals(3, values.size)
        assertTrue(values[0].single().free is CachedNumericState.Unavailable)
        assertTrue(values[1].isEmpty())
        assertEquals(CachedNumericState.Known(BigInteger("12")), values[2].single().free)
        assertEquals(listOf("observePortfolioBalances:7"), dao.reads)
    }

    @Test fun portfolioReadKeepsAllSevenNumericDiagnosticsAndCopiesAccountBytes() = runBlocking {
        val raw = row("asset", null, byteArrayOf(1), true)
        val dao = AssetReadProjectionDaoTest.Fake(listOf(raw))
        val actual = dao.getPortfolioBalances(7).single()
        assertSame(CachedNumericState.Missing, actual.free)
        assertEquals(CachedNumericState.Known(BigInteger("11")), actual.reserved)
        assertSame(CachedNumericState.Missing, actual.miscFrozen)
        assertEquals(CachedNumericState.Known(BigInteger("2")), actual.feeFrozen)
        assertEquals(CachedNumericState.Known(BigInteger("5")), actual.bonded)
        assertSame(CachedNumericState.Missing, actual.redeemable)
        assertEquals(CachedNumericState.Known(BigInteger("7")), actual.unbonding)
        raw.asset.accountId[0] = 9
        assertArrayEquals(byteArrayOf(1), actual.accountId)
        assertEquals("status", actual.status)
        assertEquals("account-name", actual.chainAccountName)
    }

    @Test fun portfolioSnapshotFailureDoesNotBecomeEmpty() = runBlocking {
        val failure = StorageFailure(Any())
        val dao = object : AssetReadProjectionDaoTest.Fake(emptyList()) {
            override suspend fun getPortfolioBalanceRows(metaId: Long): List<RawCachedAssetRow> = throw failure
        }
        assertSame(failure, runCatching { dao.getPortfolioBalances(7) }.exceptionOrNull())
    }

    @Test fun portfolioObservationFailureDoesNotBecomeEmpty() = runBlocking {
        val failure = StorageFailure(Any())
        val dao = object : AssetReadProjectionDaoTest.Fake(emptyList()) {
            override fun observePortfolioBalanceRows(metaId: Long): Flow<List<RawCachedAssetRow>> = flow { throw failure }
        }
        assertSame(failure, runCatching { dao.observePortfolioBalances(7).toList() }.exceptionOrNull())
    }
    private class StorageFailure(val marker: Any) : RuntimeException("public storage failure")
}
