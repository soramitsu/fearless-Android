package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.Gson
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import jp.co.soramitsu.commonnetworking.fearless.ChainModel
import jp.co.soramitsu.commonnetworking.fearless.FearlessChainsBuilder
import jp.co.soramitsu.commonnetworking.fearless.ResultChainInfo
import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.core_db.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.AssetRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainAssetRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainNodeRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
    lateinit var chainFetcher: ChainFetcher

    @MockK
    lateinit var fearlessChainsBuilder: FearlessChainsBuilder

    @MockK
    lateinit var gson: Gson

    lateinit var chainSyncService: ChainSyncService

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        runBlocking {
            coEvery { chainFetcher.getAssets() } returns listOf(REMOTE_ASSET)
            chainSyncService = ChainSyncService(dao, chainFetcher, fearlessChainsBuilder, gson)
        }
    }

    @Test
    fun `should insert new chain`() {
        runBlocking {
            localReturns(emptyList())
            remoteReturnsNew(listOf(REMOTE_CHAIN))
            every { gson.fromJson(ofType(String::class), ChainRemote::class.java) } returns REMOTE_CHAIN
            chainSyncService.syncUp()

            coVerify { dao.update(removed = withArg { assertTrue(it.isEmpty()) }, newOrUpdated = withArg { assertTrue(it.size == 1 && it[0].chain.id == REMOTE_ASSET.chainId) }) }
        }
    }

    @Test
    fun `should not insert the same chain`() {
        runBlocking {
            localReturns(listOf(LOCAL_CHAIN))
            remoteReturnsEmpty()

            chainSyncService.syncUp()

            coVerify { dao.update(removed = withArg { assertTrue(it.isEmpty()) }, newOrUpdated = withArg { assertTrue(it.isEmpty()) }) }
        }
    }

    @Test
    fun `should update chain`() {
        runBlocking {
            localReturns(listOf(LOCAL_CHAIN))
            remoteReturnsUpdated(listOf(REMOTE_CHAIN.copy(name = "new name")))
            every { gson.fromJson(ofType(String::class), ChainRemote::class.java) } returns REMOTE_CHAIN
            chainSyncService.syncUp()

            coVerify { dao.update(removed = withArg { assertTrue(it.isEmpty()) }, newOrUpdated = withArg { assertTrue(it.size == 1 && it[0].chain.id == REMOTE_ASSET.chainId) }) }
        }
    }

    @Test
    fun `should remove chain`() {
        runBlocking {
            localReturns(listOf(LOCAL_CHAIN))

            coEvery { fearlessChainsBuilder.getChains(any(), any()) } returns ResultChainInfo(
                emptyList(),
                emptyList(),
                listOf(REMOTE_CHAIN.chainId),
            )

            chainSyncService.syncUp()

            coVerify {
                dao.update(
                    removed = withArg { assertTrue(it.first().id == REMOTE_CHAIN.chainId) },
                    newOrUpdated = withArg { assertEquals(0, it.size) }
                )
            }
        }
    }

    private suspend fun remoteReturnsNew(chains: List<ChainRemote>) {
        coEvery { fearlessChainsBuilder.getChains(any(), any()) } returns ResultChainInfo(
            chains.map { ChainModel(it.chainId, it.hashCode().toString(), it.name) },
            emptyList(),
            emptyList(),
        )
    }

    private suspend fun remoteReturnsUpdated(chains: List<ChainRemote>) {
        coEvery { fearlessChainsBuilder.getChains(any(), any()) } returns ResultChainInfo(
            emptyList(),
            chains.map { ChainModel(it.chainId, it.hashCode().toString(), it.name) },
            emptyList(),
        )
    }

    private suspend fun remoteReturnsEmpty() {
        coEvery { fearlessChainsBuilder.getChains(any(), any()) } returns ResultChainInfo(
            emptyList(),
            emptyList(),
            emptyList()
        )
    }

    private suspend fun localReturns(chains: List<JoinedChainInfo>) {
        coEvery { dao.getJoinChainInfo() } returns chains
    }
}
