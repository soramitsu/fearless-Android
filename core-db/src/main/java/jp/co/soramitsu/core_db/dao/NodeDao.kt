package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.core_db.model.NodeLocal

@Dao
abstract class NodeDao {

    @Query("select * from nodes")
    abstract fun getNodes(): Observable<List<NodeLocal>>

    @Query("select * from nodes where link = :link")
    abstract fun getNode(link: String): Single<NodeLocal>

    @Query("select * from nodes where id = :id")
    abstract fun getNodeById(id: Int): Single<NodeLocal>

    @Query("select count(*) from nodes where link = :nodeHost")
    abstract fun getNodesCountByHost(nodeHost: String): Single<Int>

    @Query("select exists (select * from nodes where link = :nodeHost)")
    abstract fun checkNodeExists(nodeHost: String): Single<Boolean>

    @Query("DELETE FROM nodes where link = :link")
    abstract fun remove(link: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(nodes: List<NodeLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(nodes: NodeLocal): Long

    @Query("update nodes set name = :newName, link = :newHost, networkType = :networkType where id = :id")
    abstract fun updateNode(id: Int, newName: String, newHost: String, networkType: Int): Completable

    @Query("SELECT * from nodes where isDefault = 1 AND networkType = :networkType")
    abstract fun getDefaultNodeFor(networkType: Int): NodeLocal

    @Query("select * from nodes limit 1")
    abstract fun getFirstNode(): NodeLocal

    @Query("delete from nodes where id = :nodeId")
    abstract fun deleteNode(nodeId: Int): Completable
}