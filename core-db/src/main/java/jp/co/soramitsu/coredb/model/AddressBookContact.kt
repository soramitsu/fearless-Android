package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "address_book",
    indices = [Index(value = ["address", "chainId"], unique = true)]
)
data class AddressBookContact(
    val address: String,
    val name: String?,
    val chainId: String,
    val created: Long = System.currentTimeMillis()
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}
