package jp.co.soramitsu.coredb.model

import androidx.room.Entity

@Entity(
    tableName = "phishing",
    primaryKeys = ["address", "type"]
)
data class PhishingLocal(
    val address: String,
    val name: String?,
    val type: String,
    val subtype: String?
)
