package jp.co.soramitsu.core_db.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.core_db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChainDaoTest : DaoTest<ChainDao>(AppDatabase::chainDao){

    @Test
    fun shouldInsertWholeChain() = runBlocking {
        val chainInfo = createTestChain("0x00")

        dao.update(newOrUpdated = listOf(chainInfo), removed = emptyList())

        val chainsFromDb = dao.getJoinChainInfo()

        assertEquals(1, chainsFromDb.size)

        val chainFromDb = chainsFromDb.first()

        assertEquals(chainInfo.assets.size, chainFromDb.assets.size)
        assertEquals(chainInfo.nodes.size, chainFromDb.nodes.size)
    }

    @Test
    fun shouldDeleteChainWithCascade() = runBlocking {
        val chainInfo = createTestChain("0x00")

        dao.update(newOrUpdated = listOf(chainInfo), removed = emptyList())
        dao.update(removed = listOf(chainInfo.chain), newOrUpdated = emptyList())

        val assetsCursor = db.query("SELECT * FROM chain_assets", emptyArray())
        assertEquals(0, assetsCursor.count)

        val nodesCursor = db.query("SELECT * FROM chain_nodes", emptyArray())
        assertEquals(0, nodesCursor.count)
    }

    @Test
    fun shouldNotDeleteRuntimeCacheEntryAfterChainUpdate() = runBlocking {
        val chainInfo = createTestChain("0x00")

        dao.update(newOrUpdated = listOf(chainInfo), removed = emptyList())
        dao.updateRemoteRuntimeVersion(chainInfo.chain.id, remoteVersion = 1)

        dao.update(newOrUpdated = listOf(chainInfo), removed = emptyList())

        val runtimeEntry = dao.runtimeInfo(chainInfo.chain.id)

        assertNotNull(runtimeEntry)
    }

    @Test
    fun shouldDeleteRemovedNodes() = runBlocking {
        val chainInfo = createTestChain("0x00", nodesCount = 3)

        dao.update(newOrUpdated = listOf(chainInfo), removed = emptyList())

        val newChainInfo = createTestChain("0x00", nodesCount = 2)

        dao.update(newOrUpdated = listOf(newChainInfo), removed = emptyList())

        val chainFromDb2 = dao.getJoinChainInfo().first()

        assertEquals(2, chainFromDb2.nodes.size)
    }

    @Test
    fun shouldUpdate() = runBlocking {
        val chainsInitial = listOf(
            createTestChain("0x00"),
            createTestChain("0x01"),
            createTestChain("0x02"),
        )

        dao.update(newOrUpdated = chainsInitial, removed = emptyList())

        val newOrUpdated = listOf(
            createTestChain("0x00", "new name"),
            createTestChain("0x03")
        )

        val removed = listOf(
            chainOf("0x01"),
            chainOf("0x02")
        )

        dao.update(newOrUpdated = newOrUpdated, removed = removed)

        val chainsFromDb = dao.getJoinChainInfo()

        assertEquals(chainsFromDb.size, newOrUpdated.size)

        newOrUpdated.zip(chainsFromDb) { expected, actual ->
            assertEquals(expected.chain.id, actual.chain.id)
            assertEquals(expected.chain.name, actual.chain.name)
        }

        Unit
    }

    @Test
    fun shouldUpdateRuntimeVersions() {
        runBlocking {
            val chainId = "0x00"

            dao.update(newOrUpdated = listOf(createTestChain(chainId)), removed = emptyList())

            dao.updateRemoteRuntimeVersion(chainId, 1)

            checkRuntimeVersions(remote = 1, synced = 0)

            dao.updateSyncedRuntimeVersion(chainId, 1)

            checkRuntimeVersions(remote = 1, synced = 1)

            dao.updateRemoteRuntimeVersion(chainId, 2)

            checkRuntimeVersions(remote = 2, synced = 1)
        }
    }

    private suspend fun checkRuntimeVersions(remote: Int, synced: Int) {
        val runtimeInfo = dao.runtimeInfo("0x00")

        requireNotNull(runtimeInfo)

        assertEquals(runtimeInfo.remoteVersion, remote)
        assertEquals(runtimeInfo.syncedVersion, synced)
    }
}
