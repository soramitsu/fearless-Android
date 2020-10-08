package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Completable
import io.reactivex.Observable
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset

@Dao
abstract class AssetDao {
    @Query("select * from assets where accountAddress = :accountAddress")
    abstract fun observeAssets(accountAddress: String): Observable<List<AssetLocal>>

    @Query("select * from assets where accountAddress = :accountAddress and token = :token")
    abstract fun observeAsset(accountAddress: String, token: Asset.Token): Observable<AssetLocal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(assets: List<AssetLocal>): Completable
}