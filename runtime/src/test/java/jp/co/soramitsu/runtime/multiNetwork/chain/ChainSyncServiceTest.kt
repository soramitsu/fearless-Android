package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainAssetRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainNodeRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.testshared.argThat
import jp.co.soramitsu.testshared.eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner

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
                ethereumType = null
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
        minSupportedVersion = null
    )

    private val LOCAL_CHAIN = mapChainToChainLocal(REMOTE_CHAIN.toChain())

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

    @Test
    fun T() = runTest {
        channelFlow {

            val sem = Semaphore(1)

            val flow1 = flow {
                emit(1)
                kotlinx.coroutines.delay(1000)
                emit(3)
                kotlinx.coroutines.delay(1000)
                emit(5)
                kotlinx.coroutines.delay(1000)
            }

            val flow2 = flow {
                emit(2)
                emit(4)
                emit(6)
            }

            sem.acquire()
            flow1.onEach {
                send(it)
            }.launchIn(this)
            sem.release()

            sem.acquire()
            flow2.onEach {
                send(it)
            }.launchIn(this)
            sem.release()

        }.onEach {
            println("This is checkpoint: int - $it")
        }.flowOn(Dispatchers.Default).collect()
    }
}
