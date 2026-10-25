package jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class NativeBalanceSubscriptionTest {
    @Test fun `actual update system subscribes only exact native rows and rebinds the changed endpoint`() = runTest {
        val canonical = chain("solana-mainnet", "https://default.example.invalid")
        val alias = chain("solana:mainnet", "https://default.example.invalid")
        val chains = MutableStateFlow(listOf(canonical, alias))
        val registry = mock(ChainRegistry::class.java)
        whenever(registry.syncedChains).thenReturn(chains)
        val accounts = mock(MetaAccountDao::class.java)
        val root = ByteArray(32) { 1 }
        val native = ByteArray(32) { 2 }
        val wallet = MetaAccountLocal(root, CryptoType.SR25519, root, null, null, null,
            "Public fixture", true, 0, false, null, true).also { it.id = 1 }
        val info = RelationJoinedMetaAccountInfo(wallet, listOf(
            ChainAccountLocal(1, alias.id, native, native, CryptoType.ED25519, "SOL", true)
        ), emptyList())
        whenever(accounts.selectedMetaAccountInfoFlow()).thenReturn(flowOf(info))
        val subscribed = mutableListOf<Pair<String, String>>()
        val provider = object : BalanceLoader.Provider {
            override fun invoke(chain: Chain): BalanceLoader = object : BalanceLoader(chain) {
                override suspend fun loadBalance(metaAccounts: Set<MetaAccount>): List<AssetBalanceUpdateItem> = error("Unexpected batch")
                override fun subscribeBalance(metaAccount: MetaAccount) = emptyFlow<BalanceLoaderAction>().also {
                    subscribed += chain.id to chain.nodes.single().url
                }
            }
        }
        val system = BalancesUpdateSystem(registry, accounts, provider, mock(AssetDao::class.java), StandardTestDispatcher(testScheduler))
        backgroundScope.launch { system.start().collect {} }
        runCurrent()
        assertEquals(listOf(alias.id to "https://default.example.invalid"), subscribed)
        chains.value = listOf(canonical, chain(alias.id, "https://custom.example.invalid"))
        runCurrent()
        assertEquals(listOf(alias.id to "https://default.example.invalid", alias.id to "https://custom.example.invalid"), subscribed)
    }

    private fun chain(id: String, url: String): Chain = mock(Chain::class.java).also {
        whenever(it.id).thenReturn(id)
        whenever(it.ecosystem).thenReturn(Ecosystem.Substrate)
        whenever(it.nodes).thenReturn(listOf(ChainNode(url, "RPC", true, false)))
    }
}
