package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A pending receive owns an ID without making it a visible or signable wallet. */
@Entity(
    tableName = "portable_wallet_reservations",
    indices = [Index(value = ["operationId"])],
)
data class PortableWalletReservationLocal(
    @PrimaryKey val metaId: Long,
    val operationId: String,
    val afterImageSha256: String,
)
