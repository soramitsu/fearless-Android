package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserLocal(
    @PrimaryKey val address: String,
    val username: String,
    val publicKey: String,
    val cryptoType: Int,
    val networkType: Int
)