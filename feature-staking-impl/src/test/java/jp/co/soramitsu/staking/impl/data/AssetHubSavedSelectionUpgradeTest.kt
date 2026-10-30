package jp.co.soramitsu.staking.impl.data

import com.google.gson.Gson
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.westendChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainAssetRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

/**
 * Builds up to 4.3.0 saved a DOT or KSM staking choice as `<relay genesis>:<asset id>:<type>`. After the upgrade
 * the same bytes must open the Hub where the stake now lives, and fall back to the network chooser whenever the
 * Hub does not offer staking.
 */
class AssetHubSavedSelectionUpgradeTest {
    private val dot = "887a17c7-1370-4de0-97dd-5422e294fa75"
    private val ksm = "1e0c2ec6-935f-49bd-a854-5e12ee6c9f1b"
    private val wnd = "a3868e1b-922e-42d4-b73e-b41712f0843c"

    private fun production(): List<ChainRemote> =
        Gson().fromJson(File("../remote-config/production/chains/v13/chains.json").readText(), Array<ChainRemote>::class.java)
            .filter { !it.disabled }

    private fun withoutHubSwitch(catalogue: List<ChainRemote>) = catalogue.map { chain ->
        chain.copy(assets = chain.assets?.map { it.withStaking(it.staking.takeUnless { value -> value == AssetHubStaking.CATALOGUE_STAKING }) })
    }

    private fun ChainAssetRemote.withStaking(value: String?) = ChainAssetRemote(
        id, name, symbol, precision, icon, priceId, currencyId, existentialDeposit, color, isUtility, isNative,
        value, purchaseProviders, type, ethereumType, tonType, priceProvider, coinbaseUrl
    )

    private inner class Fixture(saved: String, catalogue: List<ChainRemote> = production()) : AutoCloseable {
        val chains: List<Chain> = catalogue.mapNotNull { runCatching { it.toChain() }.getOrNull() }
        val preferences = mock(Preferences::class.java)
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val state: StakingSharedState

        init {
            val repositories = mock(ChainsRepository::class.java)
            whenever(repositories.chainsFlow()).thenReturn(MutableStateFlow(chains))
            val account = mock(MetaAccount::class.java)
            whenever(account.id).thenReturn(META_ID)
            whenever(account.substrateAccountId).thenReturn(ACCOUNT)
            whenever(account.chainAccounts).thenReturn(emptyMap())
            val accounts = mock(AccountRepository::class.java)
            whenever(accounts.selectedMetaAccountFlow()).thenReturn(flowOf(account))
            // The wallet holds every utility asset of these networks, as the portfolio does.
            val held = chains.filter { AssetHubStaking.isHub(it.id) || AssetHubStaking.isMigratedRelay(it.id) }
                .flatMap { chain -> chain.assets.filter { it.isUtility } }
                .map { Asset.createEmpty(it, META_ID, ACCOUNT, minSupportedVersion = null) }
            val wallets = mock(WalletRepository::class.java)
            runBlocking {
                whenever(repositories.getChains()).thenReturn(chains)
                whenever(repositories.getChain(any())).thenAnswer { chains.first { c -> c.id == it.getArgument<String>(0) } }
                whenever(wallets.getAssets(META_ID)).thenReturn(held)
            }
            whenever(preferences.stringFlow(any(), anyOrNull())).thenReturn(flowOf(saved))
            state = StakingSharedState(mock(ChainRegistry::class.java), preferences, wallets, accounts, repositories, scope)
        }

        suspend fun settled(): StakingAssetSelection? = withTimeout(5_000) { state.settledSelection.first() }

        override fun close() {
            scope.cancel()
            // The stored bytes are the user's; only an explicit choice rewrites them.
            verify(preferences, never()).putString(any(), anyOrNull())
        }
    }

    @Test
    fun `saved Polkadot relay staking opens Polkadot Asset Hub staking`() = runBlocking {
        Fixture("$polkadotChainId:$dot:RELAYCHAIN").use {
            assertEquals(StakingAssetSelection.RelayChainStaking(AssetHubStaking.POLKADOT, dot), it.settled())
            assertEquals(AssetHubStaking.POLKADOT, it.state.chain().id)
            assertEquals("dot", it.state.currentToken().configuration.symbol)
        }
    }

    @Test
    fun `saved Kusama relay staking opens Kusama Asset Hub staking`() = runBlocking {
        Fixture("$kusamaChainId:$ksm:RELAYCHAIN").use {
            assertEquals(StakingAssetSelection.RelayChainStaking(AssetHubStaking.KUSAMA, ksm), it.settled())
            assertEquals(AssetHubStaking.KUSAMA, it.state.chain().id)
        }
    }

    @Test
    fun `saved relay pool choices open the Hub pools`() = runBlocking {
        Fixture("$polkadotChainId:$dot:POOL").use {
            assertEquals(StakingAssetSelection.Pool(AssetHubStaking.POLKADOT, dot), it.settled())
        }
        Fixture("$kusamaChainId:$ksm:POOL").use {
            assertEquals(StakingAssetSelection.Pool(AssetHubStaking.KUSAMA, ksm), it.settled())
        }
    }

    @Test
    fun `choices saved on the Hub keep working`() = runBlocking {
        Fixture("${AssetHubStaking.POLKADOT}:$dot:RELAYCHAIN").use {
            assertEquals(StakingAssetSelection.RelayChainStaking(AssetHubStaking.POLKADOT, dot), it.settled())
        }
    }

    @Test
    fun `only the Hub native assets and their pools are offered on these networks`() = runBlocking {
        Fixture("$polkadotChainId:$dot:RELAYCHAIN").use {
            val offered = it.state.availableToSelect().filter { selection ->
                AssetHubStaking.isHub(selection.chainId) || AssetHubStaking.isMigratedRelay(selection.chainId)
            }.toSet()
            assertEquals(
                setOf(
                    StakingAssetSelection.RelayChainStaking(AssetHubStaking.POLKADOT, dot),
                    StakingAssetSelection.Pool(AssetHubStaking.POLKADOT, dot),
                    StakingAssetSelection.RelayChainStaking(AssetHubStaking.KUSAMA, ksm),
                    StakingAssetSelection.Pool(AssetHubStaking.KUSAMA, ksm)
                ),
                offered
            )
        }
    }

    @Test
    fun `without the catalogue switch a saved DOT or KSM choice opens the chooser`() = runBlocking {
        listOf("$polkadotChainId:$dot:RELAYCHAIN", "$kusamaChainId:$ksm:POOL", "${AssetHubStaking.POLKADOT}:$dot:RELAYCHAIN").forEach { saved ->
            Fixture(saved, withoutHubSwitch(production())).use {
                assertNull(saved, it.settled())
                assertTrue(it.state.availableToSelect().none { selection -> AssetHubStaking.isHub(selection.chainId) })
            }
        }
    }

    @Test
    fun `a saved Westend choice opens the chooser because no Westend Hub is listed`() = runBlocking {
        Fixture("$westendChainId:$wnd:RELAYCHAIN").use {
            assertNull(it.settled())
        }
    }

    private companion object {
        const val META_ID = 42L
        val ACCOUNT = ByteArray(32) { 7 }
    }
}
