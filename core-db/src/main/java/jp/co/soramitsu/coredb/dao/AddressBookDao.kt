package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.coredb.model.AddressBookContact
import kotlinx.coroutines.flow.Flow

@Dao
interface AddressBookDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: AddressBookContact)

    @Query("delete from address_book")
    suspend fun clearTable()

    @Query("select * from address_book where :chainId = chainId")
    fun observeAddressBook(chainId: String): Flow<List<AddressBookContact>>
}
