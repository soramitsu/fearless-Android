package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nodes")
data class NodeLocal(
    @PrimaryKey val id: Int,
    val name: String,
    val link: String,
    val networkType: Int,
    val default: Boolean
)