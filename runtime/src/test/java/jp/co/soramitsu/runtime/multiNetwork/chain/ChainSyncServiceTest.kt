package jp.co.soramitsu.runtime.multiNetwork.chain

import android.util.Log
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainAssetRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainNodeRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.solana.SolanaChainDefinition
import jp.co.soramitsu.runtime.multiNetwork.chain.solana.SolanaDevnetChainDefinition
import jp.co.soramitsu.testshared.argThat
import jp.co.soramitsu.testshared.eq
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mockStatic
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.MockedStatic

@RunWith(MockitoJUnitRunner::class)
class ChainSyncServiceTest {

    private val REMOTE_CHAIN = ChainRemote(
        chainId = "0x00",
        rank = null,
        name = "Test",
        assets = listOf(
            ChainAssetRemote(
                id = "test",
                name = "test",
                precision = 10,
                priceId = "test",
                icon = "test",
                symbol = "test",
                staking = null,
                purchaseProviders = null,
                isUtility = null,
                type = null,
                currencyId = null,
                existentialDeposit = null,
                color = null,
                isNative = null,
                ethereumType = null,
                priceProvider = null,
                tonType = null,
                coinbaseUrl = null
            )
        ),
        nodes = listOf(
            ChainNodeRemote(
                url = "url",
                name = "test"
            )
        ),
        icon = "test",
        addressPrefix = 0,
        options = emptyList(),
        parentId = null,
        externalApi = null,
        minSupportedVersion = null,
        paraId = null,
        ecosystem = "Substrate"
    )

    private val LOCAL_CHAIN = mapChainToChainLocal(REMOTE_CHAIN.toChain())
    private val LOCAL_SOLANA = mapChainToChainLocal(SolanaChainDefinition.chain)
    private val LOCAL_SOLANA_DEVNET = mapChainToChainLocal(SolanaDevnetChainDefinition.chain)

    private lateinit var logMock: MockedStatic<Log>

    @Mock
    lateinit var dao: ChainDao

    @Mock
    lateinit var metaAccountDao: MetaAccountDao

    @Mock
    lateinit var assetsDao: AssetDao

    @Mock
    lateinit var chainFetcher: ChainFetcher

    @Mock
    lateinit var contextManager: ContextManager

    lateinit var chainSyncService: ChainSyncService

    @Before
    fun setup() {
        logMock = mockStatic(Log::class.java).apply {
            `when`<Int> { Log.d(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()) }.thenReturn(0)
        }
        chainSyncService = ChainSyncService(dao, chainFetcher, metaAccountDao, assetsDao, contextManager)
        `when`(metaAccountDao.getMetaAccounts()).thenReturn(emptyList())
    }

    @After
    fun tearDown() {
        logMock.close()
    }

    @Test
    fun `should insert new chain`() {
        runBlocking {
            localReturns(emptyList())
            remoteReturns(listOf(REMOTE_CHAIN))

            chainSyncService.syncUp()

            verify(dao).updateChains(
                chainsToAdd = containsChainLocalWithId(REMOTE_CHAIN.chainId),
                chainsToUpdate = eq(emptyList<ChainLocal>())
            )
        }
    }

    @Test
    fun `should not insert the same chain`() {
        runBlocking {
            localReturns(listOf(LOCAL_CHAIN, LOCAL_SOLANA, LOCAL_SOLANA_DEVNET))
            remoteReturns(listOf(REMOTE_CHAIN))

            chainSyncService.syncUp()

            verify(dao).updateChains(
                chainsToAdd = eq(emptyList<ChainLocal>()),
                chainsToUpdate = eq(emptyList<ChainLocal>())
            )
        }
    }

    @Test
    fun `should update chain`() {
        runBlocking {
            localReturns(listOf(LOCAL_CHAIN, LOCAL_SOLANA, LOCAL_SOLANA_DEVNET))


            remoteReturns(listOf(REMOTE_CHAIN.copy(name = "new name")))

            chainSyncService.syncUp()

            verify(dao).updateChains(
                chainsToAdd = eq(emptyList<ChainLocal>()),
                chainsToUpdate = containsChainLocalWithId(REMOTE_CHAIN.chainId)
            )
        }
    }

    @Test
    fun `should remove chain`() {
        runBlocking {
            localReturns(listOf(LOCAL_CHAIN, LOCAL_SOLANA, LOCAL_SOLANA_DEVNET))

            val secondChain = REMOTE_CHAIN.copy(chainId = "0x001")

            remoteReturns(listOf(secondChain))

            chainSyncService.syncUp()

            verify(dao).updateChains(
                chainsToAdd = containsChainLocalWithId(secondChain.chainId),
                chainsToUpdate = eq(emptyList<ChainLocal>())
            )
            verify(dao).deleteChains(removesChainWithId(REMOTE_CHAIN.chainId))
        }
    }

    @Test
    fun `should append solana chain when remote does not expose it`() {
        runBlocking {
            localReturns(emptyList())
            remoteReturns(emptyList())

            chainSyncService.syncUp()

            verify(dao).updateChains(
                chainsToAdd = containsChains(
                    SolanaChainDefinition.CHAIN_ID,
                    SolanaDevnetChainDefinition.CHAIN_ID
                ),
                chainsToUpdate = eq(emptyList<ChainLocal>())
            )
        }
    }

    private suspend fun remoteReturns(chains: List<ChainRemote>) {
        `when`(chainFetcher.getChains()).thenReturn(chains)
    }

    private suspend fun localReturns(chains: List<JoinedChainInfo>) {
        `when`(dao.getJoinChainInfo()).thenReturn(chains)
    }

    private fun removesChainWithId(id: String) = argThat<List<ChainLocal>> {
        it.any { chain -> chain.id == id }
    }

    private fun containsChainLocalWithId(id: String) = argThat<List<ChainLocal>> {
        it.any { chain -> chain.id == id }
    }

    private fun containsChains(vararg ids: String) = argThat<List<ChainLocal>> {
        ids.all { id -> it.any { chain -> chain.id == id } }
    }
}
