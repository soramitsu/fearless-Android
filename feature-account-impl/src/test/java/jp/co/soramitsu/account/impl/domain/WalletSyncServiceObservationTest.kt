package jp.co.soramitsu.account.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WalletSyncServiceObservationTest {
    private val dao = mockk<MetaAccountDao>(relaxed = true)
    private val chains = mockk<ChainsRepository>()
    private val registry = mockk<ChainRegistry>()
    private val assets = mockk<AssetDao>(relaxed = true)
    private val loaders = mockk<BalanceLoader.Provider>()
    private val loader = mockk<BalanceLoader>()

    private fun account(id: Long = 7, ethereum: Boolean = false, ton: Boolean = false) = RelationJoinedMetaAccountInfo(
        MetaAccountLocal(
            if (ton) null else ByteArray(32) { 1 }, CryptoType.ED25519,
            if (ton) null else ByteArray(32) { 1 }, if (ethereum) ByteArray(33) { 2 } else null,
            if (ethereum) ByteArray(20) { id.toByte() } else null, if (ton) ByteArray(32) { 3 } else null,
            "Wallet $id", false, 0, true, null, false
        ).apply { this.id = id }, emptyList(), emptyList()
    )

    @Test
    fun firstScanPersistsKnownBalancesWithoutAnExplicitVisibilityPreference() = runTest {
        val asset = AssetBalanceFixtures.configuration("native", "ton")
        val chain = VmTestFixtures.chain("ton", ecosystem = Ecosystem.Ton).copy(assets = listOf(asset))
        val wallet = account(ton = true)
        val stored = mutableListOf<List<AssetLocal>>()
        every { dao.observeNotInitializedMetaAccounts() } returns flowOf(emptyList(), listOf(wallet))
        coEvery { chains.getChains() } returns listOf(chain)
        every { loaders.invoke(chain) } returns loader
        coEvery { loader.loadBalance(any()) } returns listOf(
            AssetBalanceUpdateItem(7, "ton", byteArrayOf(3), "native", freeInPlanks = BigInteger.TEN),
            AssetBalanceUpdateItem(7, "missing-chain", byteArrayOf(3), "native"),
            AssetBalanceUpdateItem(7, "ton", byteArrayOf(3), "missing-asset")
        )
        coEvery { assets.insertAssets(capture(stored)) } returns Unit
        WalletSyncService(dao, chains, registry, mockk(), assets, loaders, StandardTestDispatcher(testScheduler)).start()
        advanceUntilIdle()
        assertEquals(1, stored.size)
        val result = stored.single().single()
        assertEquals(7L, result.metaId)
        assertEquals("ton", result.chainId)
        assertEquals("native", result.id)
        assertEquals(BigInteger.TEN, result.freeInPlanks)
        assertEquals("price-native", result.tokenPriceId)
        assertNull(result.enabled)
        coVerify(exactly = 1) { dao.markAccountsInitialized(listOf(7)) }
    }

    @Test
    fun failedFirstReadStoresUnknownBalancesAndStillMarksWalletInitialized() = runTest {
        val asset = AssetBalanceFixtures.configuration("native", "ton")
        val chain = VmTestFixtures.chain("ton", ecosystem = Ecosystem.Ton).copy(assets = listOf(asset))
        every { dao.observeNotInitializedMetaAccounts() } returns flowOf(listOf(account(ton = true)))
        coEvery { chains.getChains() } returns listOf(chain)
        coEvery { chains.getChain("ton") } returns chain
        every { loaders.invoke(chain) } returns loader
        coEvery { loader.loadBalance(any()) } throws IllegalStateException("offline")
        val stored = mutableListOf<List<AssetLocal>>()
        coEvery { assets.insertAssets(capture(stored)) } returns Unit
        WalletSyncService(dao, chains, registry, mockk(), assets, loaders, StandardTestDispatcher(testScheduler)).start()
        advanceUntilIdle()
        val result = stored.single().single()
        assertEquals(AssetBalanceUpdateItem(7, "ton", byteArrayOf(3), "native").freeInPlanks, result.freeInPlanks)
        assertNull(result.enabled)
        coVerify { dao.markAccountsInitialized(listOf(7)) }
    }

    @Test
    fun failedAssetWriteDoesNotPreventOtherWalletsFromBeingStored() = runTest {
        val asset = AssetBalanceFixtures.configuration("native", "ton")
        val chain = VmTestFixtures.chain("ton", ecosystem = Ecosystem.Ton).copy(assets = listOf(asset))
        every { dao.observeNotInitializedMetaAccounts() } returns flowOf(listOf(account(7, ton = true), account(8, ton = true)))
        coEvery { chains.getChains() } returns listOf(chain)
        every { loaders.invoke(chain) } returns loader
        coEvery { loader.loadBalance(any()) } returns listOf(
            AssetBalanceUpdateItem(7, "ton", byteArrayOf(3), "native"),
            AssetBalanceUpdateItem(8, "ton", byteArrayOf(3), "native")
        )
        coEvery { assets.insertAssets(match { it.first().metaId == 7L }) } throws IllegalStateException("write failed")
        WalletSyncService(dao, chains, registry, mockk(), assets, loaders, StandardTestDispatcher(testScheduler)).start()
        advanceUntilIdle()
        coVerify(exactly = 1) { assets.insertAssets(match { it.single().metaId == 8L }) }
        coVerify { dao.markAccountsInitialized(listOf(7, 8)) }
    }
}
