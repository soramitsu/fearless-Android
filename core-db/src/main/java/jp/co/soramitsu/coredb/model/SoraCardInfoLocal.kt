package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "sora_card"
)
data class SoraCardInfoLocal(
    @PrimaryKey val id: String,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpirationTime: Long,
    val kycStatus: String
)
