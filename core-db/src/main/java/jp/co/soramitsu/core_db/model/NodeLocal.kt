package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nodes")
data class NodeLocal(
    val name: String,
    val link: String,
    val networkType: Int,
    val isDefault: Boolean
) {
    @PrimaryKey(autoGenerate = true) var id: Int = 0
}