package jp.co.soramitsu.coredb.dao

import java.math.BigInteger
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetPreferenceDaoTest : DaoTest<AssetDao>({ it.assetDao() }) {
    @Test
    fun exactAssetIdLookupDoesNotIncludeSameSymbolContract() = runBlocking {
        val chainId = "ethereum"
        val accountId = ByteArray(20) { 4 }
        val baseChain = createTestChain(chainId)
        db.chainDao().addChain(
            baseChain.copy(
                assets = listOf(
                    baseChain.chain.assetOf("contract-a", symbol = "DUP"),
                    baseChain.chain.assetOf("contract-b", symbol = "DUP")
                )
            )
        )
        dao.insertAssets(
            listOf(
                AssetLocal.createEmpty(accountId, "contract-a", chainId, 10, null)
                    .copy(freeInPlanks = BigInteger.ONE),
                AssetLocal.createEmpty(accountId, "contract-b", chainId, 10, null)
                    .copy(freeInPlanks = BigInteger.TEN)
            )
        )

        val result = dao.getAssets(accountMetaId = 10, chainId = chainId, id = "contract-a")

        assertEquals(listOf("contract-a"), result.map { it.asset.id })
        assertEquals(listOf(BigInteger.ONE), result.map { it.asset.freeInPlanks })
    }

    @Test
    fun exactAssetLookupDoesNotIncludeSameRegistryIdFromAnotherNetwork() = runBlocking {
        val accountId = ByteArray(32) { 5 }
        db.chainDao().addChain(createTestChain("polkadot"))
        db.chainDao().addChain(createTestChain("kusama"))
        dao.insertAssets(
            listOf(
                AssetLocal.createEmpty(accountId, "0", "polkadot", 11, null)
                    .copy(freeInPlanks = BigInteger.ONE),
                AssetLocal.createEmpty(accountId, "0", "kusama", 11, null)
                    .copy(freeInPlanks = BigInteger.TEN)
            )
        )

        val result = dao.getAssets(accountMetaId = 11, chainId = "polkadot", id = "0")

        assertEquals(listOf("polkadot"), result.map { it.asset.chainId })
        assertEquals(listOf(BigInteger.ONE), result.map { it.asset.freeInPlanks })
    }

    @Test
    fun newlyDiscoveredAssetRemainsAuto() = runBlocking {
        val accountId = ByteArray(32) { 2 }
        db.chainDao().addChain(createTestChain("solana"))

        dao.updateBalanceOrInsertPreservingPreference(
            balance = AssetBalanceUpdateItem(
                metaId = 8,
                chainId = "solana",
                accountId = accountId,
                id = "new-mint",
                freeInPlanks = BigInteger.ONE
            ),
            tokenPriceId = null
        )

        assertNull(dao.getAsset(8, accountId, "solana", "new-mint")!!.asset.enabled)
    }

    @Test
    fun hiddenTombstoneSurvivesLaterDiscoveryScan() = runBlocking {
        val chainId = "solana"
        val assetId = "mint"
        val accountId = ByteArray(32) { 1 }
        db.chainDao().addChain(createTestChain(chainId))

        dao.updateOrInsertAssetPreferences(
            listOf(
                AssetUpdateItem(
                    metaId = 7,
                    chainId = chainId,
                    accountId = accountId,
                    id = assetId,
                    sortIndex = Int.MAX_VALUE,
                    enabled = false,
                    tokenPriceId = null
                )
            )
        )
        dao.updateBalanceOrInsertPreservingPreference(
            balance = AssetBalanceUpdateItem(
                metaId = 7,
                chainId = chainId,
                accountId = accountId,
                id = assetId,
                freeInPlanks = BigInteger.valueOf(42)
            ),
            tokenPriceId = null
        )

        val stored = dao.getAsset(7, accountId, chainId, assetId)!!.asset
        assertFalse(stored.enabled!!)
        assertEquals(BigInteger.valueOf(42), stored.freeInPlanks)
    }

    @Test
    fun shownPreferenceSurvivesLaterDiscoveryScan() = runBlocking {
        val accountId = ByteArray(32) { 3 }
        db.chainDao().addChain(createTestChain("solana"))
        dao.updateOrInsertAssetPreferences(
            listOf(
                AssetUpdateItem(
                    metaId = 9,
                    chainId = "solana",
                    accountId = accountId,
                    id = "shown-mint",
                    sortIndex = Int.MAX_VALUE,
                    enabled = true,
                    tokenPriceId = null
                )
            )
        )

        dao.updateBalanceOrInsertPreservingPreference(
            AssetBalanceUpdateItem(
                metaId = 9,
                chainId = "solana",
                accountId = accountId,
                id = "shown-mint",
                freeInPlanks = BigInteger.TEN
            ),
            tokenPriceId = null
        )

        assertTrue(dao.getAsset(9, accountId, "solana", "shown-mint")!!.asset.enabled!!)
    }
}
