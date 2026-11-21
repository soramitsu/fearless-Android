package jp.co.soramitsu.account.impl.domain

import android.util.Log
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.nomis.NomisApi
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.NomisScoresDao
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

/**
 * The first scan of a newly added wallet reads every network. A network background sync does not
 * keep is connected for that read only, through the registry's discovery lease, so the scan leaves
 * no connection behind (the loaders connect a network they read and never disconnect it).
 */
class WalletSyncServiceFirstScanTest {

    private val account = mock(MetaAccount::class.java)
    private val registry = mock(ChainRegistry::class.java)
    private val loader = mock(BalanceLoader::class.java)
    private val loaders = mock(BalanceLoader.Provider::class.java)

    private val service = WalletSyncService(
        metaAccountDao = mock(MetaAccountDao::class.java),
        chainsRepository = mock(ChainsRepository::class.java),
        chainRegistry = registry,
        remoteStorageSource = mock(RemoteStorageSource::class.java),
        assetDao = mock(AssetDao::class.java),
        nomisApi = mock(NomisApi::class.java),
        nomisScoresDao = mock(NomisScoresDao::class.java),
        balanceLoaderProvider = loaders
    )

    @Test
    fun `a substrate or evm network is read through the registry's discovery lease`() = runTest {
        val chain = chain("acala", Ecosystem.Substrate)
        val read = listOf(update(chain.id))
        whenever(loaders.invoke(chain)).thenReturn(loader)
        whenever(loader.loadBalance(setOf(account))).thenReturn(read)
        whenever(registry.withDiscoveryConnection(eq(chain), any<suspend () -> Any?>())).doSuspendableAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[1] as suspend () -> Any?).invoke()
        }

        assertEquals(read, service.readNewWalletBalances(chain, setOf(account)))
        verify(registry).withDiscoveryConnection(eq(chain), any<suspend () -> Any?>())
    }

    @Test
    fun `an app-owned network is read on its own client without a lease`() = runTest {
        val chain = chain(UniversalWalletRegistry.solanaMainnet.id, Ecosystem.Substrate)
        val read = listOf(update(chain.id))
        whenever(loaders.invoke(chain)).thenReturn(loader)
        whenever(loader.loadBalance(setOf(account))).thenReturn(read)

        assertEquals(read, service.readNewWalletBalances(chain, setOf(account)))
        verify(registry, never()).withDiscoveryConnection(any(), any<suspend () -> Any?>())
    }

    @Test
    fun `a read that does not finish in 15 s leaves the network's rows unknown`() = runTest {
        val chain = chain(UniversalWalletRegistry.solanaMainnet.id, Ecosystem.Substrate)
        whenever(loaders.invoke(chain)).thenReturn(loader)
        whenever(loader.loadBalance(setOf(account))).doSuspendableAnswer { awaitCancellation() }

        assertNull(service.readNewWalletBalances(chain, setOf(account)))
    }

    @Test
    fun `a network that cannot be connected leaves its rows unknown without failing the scan`() = runTest {
        val androidLog = mockStatic(Log::class.java)
        try {
            val chain = chain("broken", Ecosystem.Substrate)
            whenever(loaders.invoke(chain)).thenReturn(loader)
            whenever(registry.withDiscoveryConnection(eq(chain), any<suspend () -> Any?>()))
                .doSuspendableAnswer { error("Runtime target is unavailable") }

            assertNull(service.readNewWalletBalances(chain, setOf(account)))
        } finally {
            androidLog.close()
        }
    }

    private fun update(chainId: String) = AssetBalanceUpdateItem(
        metaId = 1, chainId = chainId, accountId = byteArrayOf(1), id = "native", freeInPlanks = BigInteger.TEN
    )

    private fun chain(id: String, ecosystem: Ecosystem): Chain = mock(Chain::class.java).also { chain ->
        whenever(chain.id).thenReturn(id)
        whenever(chain.ecosystem).thenReturn(ecosystem)
        whenever(chain.nodes).thenReturn(listOf(ChainNode("wss://$id.invalid", id, true, true)))
    }
}
