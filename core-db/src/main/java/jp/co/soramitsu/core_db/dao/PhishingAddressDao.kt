package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.PhishingAddressLocal

@Dao
interface PhishingAddressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(addresses: List<PhishingAddressLocal>)

    @Query("delete from phishing_addresses")
    suspend fun clearTable()

    @Query("select publicKey from phishing_addresses")
    suspend fun getAllAddresses(): List<String>
}
