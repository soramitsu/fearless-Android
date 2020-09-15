package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.core_db.model.NodeLocal

@Dao
abstract class NodeDao {

    @Query("select * from nodes")
    abstract fun getNodes(): Observable<List<NodeLocal>>

    @Query("select * from nodes where link = :link")
    abstract fun getNode(link: String): Single<NodeLocal>

    @Query("DELETE FROM nodes where link = :link")
    abstract fun remove(link: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(nodes: List<NodeLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(nodes: NodeLocal): Long

    @Query("SELECT * from nodes where isDefault = 1 AND networkType = :networkType")
    abstract fun getDefaultNodeFor(networkType: Int): NodeLocal
}