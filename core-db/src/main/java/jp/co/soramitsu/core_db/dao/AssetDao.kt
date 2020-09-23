package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Completable
import io.reactivex.Observable
import jp.co.soramitsu.core_db.model.AccountLocal
import jp.co.soramitsu.core_db.model.AssetLocal

@Dao
abstract class AssetDao {
    @Query("select * from assets where accountAddress = :accountAddress")
    abstract fun observeAssets(accountAddress: String): Observable<List<AssetLocal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(assets: List<AssetLocal>)
}