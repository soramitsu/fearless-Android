package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.core_db.model.chain.ChainLocal
import jp.co.soramitsu.core_db.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainAssetRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainNodeRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.test_shared.argThat
import jp.co.soramitsu.test_shared.eq
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ChainSyncServiceTest {

    private val REMOTE_CHAIN = ChainRemote(
        chainId = "0x00",
        name = "Test",
        assets = listOf(
            ChainAssetRemote(
                assetId = 0,
                symbol = "TEST",
                precision = 10,
                name = "Test"
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
        types = null,
        options = emptyList(),
        parentId = null
    )

    private val LOCAL_CHAIN = mapChainToChainLocal(mapChainRemoteToChain(REMOTE_CHAIN))

    @Mock
    lateinit var dao: ChainDao

    @Mock
    lateinit var chainFetcher: ChainFetcher

    lateinit var chainSyncService: ChainSyncService

    @Before
    fun setup() {
        chainSyncService = ChainSyncService(dao, chainFetcher)
    }

    @Test
    fun `should insert new chain`() {
        runBlocking {
            localReturns(emptyList())
            remoteReturns(listOf(REMOTE_CHAIN))

            chainSyncService.syncUp()

            verify(dao).update(removed = eq(emptyList()), newOrUpdated = insertsChainWithId(REMOTE_CHAIN.chainId))
        }
    }

    @Test
    fun `should not insert the same chain`() {
        runBlocking {
            localReturns(listOf(LOCAL_CHAIN))
            remoteReturns(listOf(REMOTE_CHAIN))

            chainSyncService.syncUp()

            verify(dao).update(removed = eq(emptyList()), newOrUpdated = eq(emptyList()))
        }
    }

    @Test
    fun `should update chain`() {
        runBlocking {
            localReturns(listOf(LOCAL_CHAIN))


            remoteReturns(listOf(REMOTE_CHAIN.copy(name = "new name")))

            chainSyncService.syncUp()

            verify(dao).update(removed = eq(emptyList()), newOrUpdated = insertsChainWithId(REMOTE_CHAIN.chainId))
        }
    }

    @Test
    fun `should remove chain`() {
        runBlocking {
            localReturns(listOf(LOCAL_CHAIN))

            val secondChain = REMOTE_CHAIN.copy(chainId = "0x001")

            remoteReturns(listOf(secondChain))

            chainSyncService.syncUp()

            verify(dao).update(
                removed = removesChainWithId(REMOTE_CHAIN.chainId),
                newOrUpdated = insertsChainWithId(secondChain.chainId)
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
        it.size == 1 && it.first().id == id
    }

    private fun insertsChainWithId(id: String) = argThat<List<JoinedChainInfo>> {
        it.size == 1 && it.first().chain.id == id
    }
}
