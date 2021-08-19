package jp.co.soramitsu.core_db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.chain.ChainAssetLocal
import jp.co.soramitsu.core_db.model.chain.ChainLocal
import jp.co.soramitsu.core_db.model.chain.ChainNodeLocal
import jp.co.soramitsu.core_db.model.chain.JoinedChainInfo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SimpleEntityReadWriteTest {
    private lateinit var chainDao: ChainDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .build()

        chainDao = db.chainDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun shouldInsertWholeChain() = runBlocking {
        val chainInfo = createTestChain("0x00")

        chainDao.update(newOrUpdated = listOf(chainInfo), removed = emptyList())

        val chainsFromDb = chainDao.getJoinChainInfo()

        assertEquals(1, chainsFromDb.size)

        val chainFromDb = chainsFromDb.first()

        assertEquals(chainInfo.assets.size, chainFromDb.assets.size)
        assertEquals(chainInfo.nodes.size, chainFromDb.nodes.size)
    }

    @Test
    fun shouldDeleteChainWithCascade() = runBlocking {
        val chainInfo = createTestChain("0x00")

        chainDao.update(newOrUpdated = listOf(chainInfo), removed = emptyList())
        chainDao.update(removed = listOf(chainInfo.chain), newOrUpdated = emptyList())

        val assetsCursor = db.query("SELECT * FROM chain_assets", emptyArray())
        assertEquals(0, assetsCursor.count)

        val nodesCursor = db.query("SELECT * FROM chain_nodes", emptyArray())
        assertEquals(0, nodesCursor.count)
    }

    @Test
    fun shouldDeleteRemovedNodes() = runBlocking {
        val chainInfo = createTestChain("0x00", nodesCount = 3)

        chainDao.update(newOrUpdated = listOf(chainInfo), removed = emptyList())

        val newChainInfo = createTestChain("0x00", nodesCount = 2)

        chainDao.update(newOrUpdated = listOf(newChainInfo), removed = emptyList())

        val chainFromDb2 = chainDao.getJoinChainInfo().first()

        assertEquals(2, chainFromDb2.nodes.size)
    }

    @Test
    fun shouldUpdate() = runBlocking {
        val chainsInitial = listOf(
            createTestChain("0x00"),
            createTestChain("0x01"),
            createTestChain("0x02"),
        )

        chainDao.update(newOrUpdated = chainsInitial, removed = emptyList())

        val newOrUpdated = listOf(
            createTestChain("0x00", "new name"),
            createTestChain("0x03")
        )

        val removed = listOf(
            chainOf("0x01"),
            chainOf("0x02")
        )

        chainDao.update(newOrUpdated = newOrUpdated, removed = removed)

        val chainsFromDb = chainDao.getJoinChainInfo()

        assertEquals(chainsFromDb.size, newOrUpdated.size)

        newOrUpdated.zip(chainsFromDb) { expected, actual ->
            assertEquals(expected.chain.id, actual.chain.id)
            assertEquals(expected.chain.name, actual.chain.name)
        }

        Unit
    }

    private fun createTestChain(
        id: String,
        name: String = id,
        nodesCount: Int = 3
    ): JoinedChainInfo {
        val chain = chainOf(id, name)
        val nodes = with(chain) {
            (1..nodesCount).map {
                nodeOf("link${it}")
            }
        }
        val assets = with(chain) {
            listOf(
                assetOf(0, symbol = "A"),
                assetOf(1, symbol = "B")
            )
        }

        return JoinedChainInfo(chain, nodes, assets)
    }

    private fun chainOf(
        id: String,
        name: String = id,
    ) = ChainLocal(
        id = id,
        parentId = null,
        name = name,
        icon = "Test",
        types = null,
        prefix = 0,
        isTestNet = false,
        isEthereumBased = false
    )

    private fun ChainLocal.nodeOf(
        link: String,
    ) = ChainNodeLocal(
        name = "Test",
        url = link,
        chainId = id
    )

    private fun ChainLocal.assetOf(
        assetId: Int,
        symbol: String,
    ) = ChainAssetLocal(
        name = "Test",
        chainId = id,
        symbol = symbol,
        id = assetId,
        precision = 10
    )
}
