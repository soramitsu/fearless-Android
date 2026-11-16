package jp.co.soramitsu.app.root.domain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.model.AssetDiscoveryCoverage
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.NetworkScanStateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** One sweep batch: its schedule, a broken network that must not stall the cursor, and timed-out reads. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductionAssetDiscoveryBatchTest {
    private val preferences = mockk<Preferences>(relaxed = true)
    private val chains = mockk<ChainsRepository>()
    private val wallet = mockk<MetaAccount>(relaxed = true) {
        every { id } returns 5
        every { initialized } returns true
    }
    private val accounts = mockk<AccountRepository> { coEvery { allMetaAccounts() } returns listOf(wallet) }
    private val loaders = mockk<BalanceLoader.Provider>()
    private val scanState = mockk<NetworkScanStateStore>(relaxed = true)
    private val sweep = ProductionAssetDiscoverySweep(
        chains, accounts, loaders, mockk<AssetDao>(relaxed = true), mockk<ChainRegistry>(relaxed = true), scanState, preferences
    )

    @Test
    fun theNextSweepIsDueADayAfterTheLastCompletedOne() {
        val completedAt = 1_000_000L
        every { preferences.getString(ProductionAssetDiscoverySweep.PREF_CURSOR_CHAIN_ID) } returns null
        every { preferences.getLong(ProductionAssetDiscoverySweep.PREF_LAST_COMPLETED_AT, 0L) } returns completedAt
        val hour = 60L * 60L * 1_000L
        assertEquals(23 * hour, sweep.millisUntilDue(completedAt + hour))
        assertEquals(0L, sweep.millisUntilDue(completedAt + ProductionAssetDiscoverySweep.SWEEP_INTERVAL_MILLIS))
    }

    @Test
    fun anEmptyCatalogCompletesTheSweep() = runTest {
        every { preferences.getString(ProductionAssetDiscoverySweep.PREF_CURSOR_CHAIN_ID) } returns null
        coEvery { chains.getChains() } returns emptyList()
        val result = sweep.scanNextBatch()
        assertEquals(emptyList<String>(), result.attemptedChainIds)
        assertFalse(result.hasMore)
        verify { preferences.removeField(ProductionAssetDiscoverySweep.PREF_CURSOR_CHAIN_ID) }
        verify { preferences.putLong(ProductionAssetDiscoverySweep.PREF_LAST_COMPLETED_AT, any()) }
    }

    @Test
    fun aBrokenNetworkStillAdvancesTheCursor() = runTest {
        every { preferences.getString(ProductionAssetDiscoverySweep.PREF_CURSOR_CHAIN_ID) } returns null
        val broken = offline("a-broken", Ecosystem.Ton)
        val next = offline("b-next", Ecosystem.Ton)
        coEvery { chains.getChains() } returns listOf(next, broken)
        every { loaders.invoke(broken) } returns loader(broken) { throw IOException("endpoint down") }
        every { loaders.invoke(next) } returns loader(next) { emptyList() }
        val result = sweep.scanNextBatch(batchSize = 1)
        assertEquals(listOf("a-broken"), result.attemptedChainIds)
        assertEquals(true, result.hasMore)
        verify { preferences.putString(ProductionAssetDiscoverySweep.PREF_CURSOR_CHAIN_ID, "a-broken") }
    }

    @Test
    fun aTimedOutReadRecordsAFailureWithTheNetworksDiscoveryCoverage() = runTest {
        every { preferences.getString(ProductionAssetDiscoverySweep.PREF_CURSOR_CHAIN_ID) } returns null
        val ton = offline("a-ton", Ecosystem.Ton)
        val bitcoin = offline("b-bitcoin", Ecosystem.Substrate, history = Chain.ExternalApi.Section.Type.BITCOIN)
        val solana = offline("c-solana", Ecosystem.Substrate, history = Chain.ExternalApi.Section.Type.SOLANA)
        val substrate = offline("d-substrate", Ecosystem.Substrate)
        val all = listOf(ton, bitcoin, solana, substrate)
        coEvery { chains.getChains() } returns all
        all.forEach { chain -> every { loaders.invoke(chain) } returns loader(chain) { awaitCancellation() } }

        sweep.scanNextBatch()

        mapOf(
            "a-ton" to AssetDiscoveryCoverage.Complete,
            "b-bitcoin" to AssetDiscoveryCoverage.Limited,
            "c-solana" to AssetDiscoveryCoverage.Complete,
            "d-substrate" to AssetDiscoveryCoverage.CatalogOnly
        ).forEach { (chainId, coverage) ->
            verify { scanState.scanFailedPreservingCoverage(5, chainId, coverage, "Asset discovery timed out", any()) }
        }
    }

    private fun offline(id: String, ecosystem: Ecosystem, history: Chain.ExternalApi.Section.Type? = null) =
        mockk<Chain>(relaxed = true) {
            every { this@mockk.id } returns id
            every { isTestNet } returns false
            every { this@mockk.ecosystem } returns ecosystem
            every { nodes } returns emptyList()
            every { assets } returns emptyList()
            every { externalApi } returns history?.let { type ->
                Chain.ExternalApi(staking = null, history = Chain.ExternalApi.Section(type, "https://indexer.example"), crowdloans = null)
            }
        }

    private fun loader(chain: Chain, read: suspend () -> List<jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem>) =
        object : BalanceLoader(chain) {
            override suspend fun loadBalance(metaAccounts: Set<MetaAccount>) = read()
            override fun subscribeBalance(metaAccount: MetaAccount) = kotlinx.coroutines.flow.emptyFlow<BalanceLoaderAction>()
        }
}
