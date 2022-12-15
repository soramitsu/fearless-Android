package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.coredb.model.PhishingLocal

@Dao
interface PhishingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(addresses: List<PhishingLocal>)

    @Query("delete from phishing")
    suspend fun clearTable()

    @Query("select address from phishing")
    suspend fun getAllAddresses(): List<String>

    @Query("select * from phishing where lower(address) = lower(:address)")
    suspend fun getPhishingInfo(address: String): PhishingLocal?
}
