package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.Gson
import io.mockk.MockKAnnotations
import io.mockk.MockKStaticScope
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import jp.co.soramitsu.commonnetworking.fearless.FearlessChainsBuilder
import jp.co.soramitsu.commonnetworking.fearless.ResultChainInfo
import jp.co.soramitsu.commonnetworking.networkclient.SoraNetworkClient
import jp.co.soramitsu.commonnetworking.networkclient.createJsonRequest
import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.core_db.model.chain.ChainLocal
import jp.co.soramitsu.core_db.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.AssetRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainAssetRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainNodeRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.test_shared.argThat
import jp.co.soramitsu.test_shared.eq
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyString
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

class ChainSyncServiceTest {

    private val REMOTE_CHAIN = ChainRemote(
        chainId = "0x00",
        name = "Test",
        assets = listOf(
            ChainAssetRemote(
                assetId = "test",
                staking = null,
                purchaseProviders = null
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
        parentId = null,
        externalApi = null,
        minSupportedVersion = ""
    )

    private val REMOTE_ASSET = AssetRemote(
        id = "test",
        chainId = "0x00",
        precision = 10,
        priceId = "test",
        icon = "test"
    )

    private val LOCAL_CHAIN = mapChainToChainLocal(mapChainRemoteToChain(listOf(REMOTE_CHAIN to ""), listOf(REMOTE_ASSET))[0])

    @MockK
    lateinit var dao: ChainDao

    @MockK
    lateinit var soraNetworkClient: SoraNetworkClient

    @MockK
    lateinit var fearlessChainsBuilder: FearlessChainsBuilder

    @MockK
    lateinit var gson: Gson

    lateinit var chainSyncService: ChainSyncService

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        runBlocking {
            mockkStatic(SoraNetworkClient::class) {

            }
            every { SoraNetworkClient.createJsonRequest<List<AssetRemote>>(any(), any(), any(), any()) } returns listOf(REMOTE_ASSET)
            chainSyncService = ChainSyncService(dao, soraNetworkClient, fearlessChainsBuilder, gson)
        }
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
        `when`(fearlessChainsBuilder.getChains(anyString(), anyList())).thenReturn(ResultChainInfo(emptyList(), emptyList(), emptyList()))
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
