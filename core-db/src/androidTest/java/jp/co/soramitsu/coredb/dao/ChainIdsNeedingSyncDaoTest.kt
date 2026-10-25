package jp.co.soramitsu.coredb.dao

import jp.co.soramitsu.coredb.model.AssetLocal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

/**
 * Background sync keeps a live balance subscription for every chain whose balance the portfolio
 * shows or reports: an explicitly shown asset, or any asset that holds a balance. 4.2.0 wrote
 * enabled = 1 for funded rows; since sync leaves new rows automatic, a funded automatic row must
 * keep its chain connected too. A funded hidden row does as well: 4.2.0 hid empty assets itself and
 * a later deposit into one of them is what the "hidden assets hold funds" notice announces.
 */
class ChainIdsNeedingSyncDaoTest : DaoTest<AssetDao>({ it.assetDao() }) {

    @Test
    fun shownAndFundedRowsNeedSyncWhileEmptyLoadingAndErroredRowsDoNot() = runBlocking {
        val chains = listOf(
            "shown-zero", "funded-auto", "reserved-auto", "empty-auto", "loading-auto",
            "errored-auto", "hidden-funded", "hidden-empty", "other-wallet-funded"
        )
        chains.forEach { db.chainDao().addChain(createTestChain(it)) }
        dao.insertAssets(
            listOf(
                row("shown-zero", enabled = true, free = BigInteger.ZERO),
                row("funded-auto", enabled = null, free = BigInteger.TEN),
                row("reserved-auto", enabled = null, free = BigInteger.ZERO, reserved = BigInteger.ONE),
                row("empty-auto", enabled = null, free = BigInteger.ZERO, reserved = BigInteger.ZERO),
                row("loading-auto", enabled = null, free = null),
                row("errored-auto", enabled = null, free = BigInteger.valueOf(-1)),
                row("hidden-funded", enabled = false, free = BigInteger.TEN),
                row("hidden-empty", enabled = false, free = BigInteger.ZERO),
                row("other-wallet-funded", enabled = null, free = BigInteger.valueOf(2), metaId = 2)
            )
        )

        assertEquals(
            setOf("shown-zero", "funded-auto", "reserved-auto", "hidden-funded", "other-wallet-funded"),
            dao.observeChainIdsNeedingSync().first()
        )
    }

    private fun row(
        chainId: String,
        enabled: Boolean?,
        free: BigInteger?,
        reserved: BigInteger? = null,
        metaId: Long = 1
    ) = AssetLocal.createEmpty(byteArrayOf(1), "0", chainId, metaId, null).copy(
        enabled = enabled,
        freeInPlanks = free,
        reservedInPlanks = reserved
    )
}
