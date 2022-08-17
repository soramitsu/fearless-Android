package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class AccountLocal(
    @PrimaryKey val address: String,
    val username: String,
    val publicKey: String,
    val cryptoType: Int,
    val position: Int
)
