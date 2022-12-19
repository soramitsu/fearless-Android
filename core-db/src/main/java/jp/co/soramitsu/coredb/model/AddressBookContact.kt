package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "address_book")
data class AddressBookContact(
    val address: String,
    val name: String?,
    val chainId: String,
    val created: Long = System.currentTimeMillis()
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}
