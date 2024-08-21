package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "allpools",
    primaryKeys = ["tokenIdBase", "tokenIdTarget"],
)
data class BasicPoolLocal(
    val tokenIdBase: String,
    val tokenIdTarget: String,
    val reserveBase: BigDecimal,
    val reserveTarget: BigDecimal,
    val totalIssuance: BigDecimal,
    val reservesAccount: String,
)

@Entity(
    tableName = "userpools",
    primaryKeys = ["userTokenIdBase", "userTokenIdTarget", "accountAddress"],
    indices = [Index(value = ["accountAddress"])],
    foreignKeys = [
        ForeignKey(
            entity = BasicPoolLocal::class,
            parentColumns = ["tokenIdBase", "tokenIdTarget"],
            childColumns = ["userTokenIdBase", "userTokenIdTarget"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ]
)
data class UserPoolLocal(
    val userTokenIdBase: String,
    val userTokenIdTarget: String,
    val accountAddress: String,
    val poolProvidersBalance: BigDecimal,
)

/*
@Entity(
    tableName = "poolBaseTokens"
)
data class PoolBaseTokenLocal(
    @PrimaryKey val tokenId: String,
    val dexId: Int,
)
*/
