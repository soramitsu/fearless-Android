package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import jp.co.soramitsu.core_db.model.NodeLocal
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NodeDao {

    @Query("select * from nodes")
    abstract fun nodesFlow(): Flow<List<NodeLocal>>

    @Query("select * from nodes")
    abstract suspend fun getNodes(): List<NodeLocal>

    @Query("select * from nodes where link = :link")
    abstract suspend fun getNode(link: String): NodeLocal

    @Query("select * from nodes where id = :id")
    abstract suspend fun getNodeById(id: Int): NodeLocal

    @Query("select count(*) from nodes where link = :nodeHost")
    abstract suspend fun getNodesCountByHost(nodeHost: String): Int

    @Query("select exists (select * from nodes where link = :nodeHost)")
    abstract suspend fun checkNodeExists(nodeHost: String): Boolean

    @Query("DELETE FROM nodes where link = :link")
    abstract suspend fun remove(link: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(nodes: List<NodeLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(nodes: NodeLocal): Long

    @Query("update nodes set name = :newName, link = :newHost, networkType = :networkType where id = :id")
    abstract suspend fun updateNode(id: Int, newName: String, newHost: String, networkType: Int)

    @Query("SELECT * from nodes where isDefault = 1 AND networkType = :networkType")
    abstract suspend fun getDefaultNodeFor(networkType: Int): NodeLocal

    @Query("select * from nodes limit 1")
    abstract suspend fun getFirstNode(): NodeLocal

    @Query("delete from nodes where id = :nodeId")
    abstract suspend fun deleteNode(nodeId: Int)

    @Query("UPDATE nodes SET isActive = 1 WHERE id = :newActiveNodeId")
    protected abstract suspend fun makeActive(newActiveNodeId: Int)

    @Query("UPDATE nodes SET isActive = 0 WHERE isActive = 1")
    protected abstract suspend fun inactiveCurrentNode()

    @Query("SELECT * FROM nodes WHERE isActive = 1")
    abstract fun activeNodeFlow(): Flow<NodeLocal?>

    @Transaction
    open suspend fun switchActiveNode(newNodeId: Int) {
        inactiveCurrentNode()

        makeActive(newNodeId)
    }
}
