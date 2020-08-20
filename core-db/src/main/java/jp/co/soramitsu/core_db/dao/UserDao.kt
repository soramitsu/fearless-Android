package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Single
import jp.co.soramitsu.core_db.model.UserLocal

@Dao
abstract class UserDao {

    @Query("select * from users")
    abstract fun getUsers(): Single<List<UserLocal>>

    @Query("select * from users where address = :address")
    abstract fun getUser(address: String): Single<UserLocal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(users: List<UserLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(user: UserLocal): Long
}