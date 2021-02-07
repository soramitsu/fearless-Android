package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "phishing_addresses")
data class PhishingAddressLocal(
    val publicKey: String
) {
    @PrimaryKey(autoGenerate = true) var id: Int = 0
}