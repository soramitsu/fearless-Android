package jp.co.soramitsu.staking.impl.data

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import jp.co.soramitsu.core.models.Asset as CoreAsset

class StakingCatalogAvailabilityTest {
    private fun chain(
        name: String = "Initial chain",
        withAsset: Boolean = true,
        staking: CoreAsset.StakingType = CoreAsset.StakingType.RELAYCHAIN
    ): Chain {
        val asset = mock(CoreAsset::class.java)
        whenever(asset.id).thenReturn("0")
        whenever(asset.chainId).thenReturn("a")
        whenever(asset.staking).thenReturn(staking)
        val chain = mock(Chain::class.java)
        whenever(chain.id).thenReturn("a")
        whenever(chain.name).thenReturn(name)
        whenever(chain.ecosystem).thenReturn(Ecosystem.Substrate)
        whenever(chain.assets).thenReturn(if (withAsset) listOf(asset) else emptyList())
        whenever(chain.assetsById).thenReturn(if (withAsset) mapOf("0" to asset) else emptyMap())
        return chain
    }

    private inner class Fixture : AutoCloseable {
        val catalog = MutableStateFlow(listOf(chain()))
        val failures = mutableListOf<Throwable>()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob() + CoroutineExceptionHandler { _, error -> failures += error })
        val preferences = mock(Preferences::class.java)
        val state: StakingSharedState

        init {
            val registry = mock(ChainRegistry::class.java)
            val repositories = mock(ChainsRepository::class.java)
            whenever(repositories.chainsFlow()).thenReturn(catalog)
            val accounts = mock(AccountRepository::class.java)
            val account = mock(MetaAccount::class.java)
            whenever(account.id).thenReturn(42L)
            whenever(account.substrateAccountId).thenReturn(ByteArray(32) { 7 })
            whenever(account.chainAccounts).thenReturn(emptyMap())
            whenever(accounts.selectedMetaAccountFlow()).thenReturn(flowOf(account))
            val wallets = mock(WalletRepository::class.java)
            val initialAsset = catalog.value.single().assets.single()
            val asset = Asset.createEmpty(initialAsset, 42L, ByteArray(32) { 7 }, minSupportedVersion = null)
            runBlocking {
                whenever(repositories.getChains()).thenAnswer { catalog.value }
                whenever(repositories.getChain(any())).thenAnswer { catalog.value.first { c -> c.id == it.getArgument<String>(0) } }
                whenever(wallets.getAssets(42L)).thenReturn(listOf(asset))
            }
            whenever(preferences.stringFlow(any(), anyOrNull())).thenReturn(flowOf("a:0:RELAYCHAIN"))
            state = StakingSharedState(registry, preferences, wallets, accounts, repositories, scope)
            runBlocking { assertEquals("a", state.chainId()) }
        }

        override fun close() {
            runBlocking { scope.coroutineContext.job.cancelAndJoin() }
            verify(preferences, never()).putString(any(), anyOrNull())
            assertTrue("No swallowed supervisor failures: $failures", failures.isEmpty())
        }
    }

    @Test
    fun healthySelectionHasCurrentChainAndAvailableOptions() = runBlocking {
        Fixture().use {
            assertEquals("Initial chain", it.state.chain().name)
            assertEquals(listOf("a"), it.state.availableToSelect().map { item -> item.chainId })
        }
    }

    @Test
    fun removedCatalogChainIsUnavailableToAFreshCollector() = runBlocking {
        Fixture().use {
            it.catalog.value = emptyList()
            assertNull(withTimeoutOrNull(150) { it.state.chain() })
        }
    }

    @Test
    fun removedCatalogAssetIsUnavailableToAFreshCollector() = runBlocking {
        Fixture().use {
            it.catalog.value = listOf(chain(withAsset = false))
            assertNull(withTimeoutOrNull(150) { it.state.currentToken() })
        }
    }

    @Test
    fun refreshedCatalogMetadataReplacesTheSelectedChain() = runBlocking {
        Fixture().use {
            it.catalog.value = listOf(chain("Updated chain"))
            assertEquals("Updated chain", it.state.chain().name)
        }
    }

    @Test
    fun disappearedSelectionRevokesCapturedActionAuthority() = runBlocking {
        Fixture().use {
            val selected = it.state.selectionRevision.capture()
            assertTrue(it.state.selectionRevision.matchesAction(selected, StakingType.RELAYCHAIN, "a", "0"))
            it.catalog.value = emptyList()
            assertFalse(it.state.selectionRevision.matchesAction(selected, StakingType.RELAYCHAIN, "a", "0"))
        }
    }

    @Test
    fun removedCatalogChainLeavesNoStakingOptions() = runBlocking {
        Fixture().use {
            it.catalog.value = emptyList()
            assertEquals(emptyList<String>(), withTimeoutOrNull(150) { it.state.availableToSelect().map { item -> item.chainId } })
        }
    }

    @Test
    fun removedStakingSettlesTheStoredChoiceAsUnavailableWithoutReplacingIt() = runBlocking {
        Fixture().use {
            assertEquals("a", it.state.settledSelectionOrNull()?.chainId)
            // Relay staking left the catalog (as on Polkadot and Kusama after the Asset Hub migration).
            it.catalog.value = listOf(chain(staking = CoreAsset.StakingType.UNSUPPORTED))
            val settledUnavailable = withTimeoutOrNull(1_000) {
                it.state.settledSelection.first { selection -> selection == null }
                true
            }
            assertEquals(true, settledUnavailable)
            assertNull(it.state.settledSelectionOrNull())
            assertEquals(emptyList<String>(), it.state.availableToSelect().map { item -> item.chainId })
        }
    }
}
