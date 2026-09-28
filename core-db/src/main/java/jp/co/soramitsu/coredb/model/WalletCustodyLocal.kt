package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Forward-only custody evidence. A missing row means UNKNOWN, including every pre-v78 wallet.
 * WATCH binds every public root and chain identity and is written only by atomic public-only
 * enrollment. SIGNED binds wallet roots and needs validated secret access; each chain signing
 * source is independently validated during capture. Neither marker replaces a secret check.
 */
@Entity(
    tableName = "wallet_custody",
    foreignKeys = [
        ForeignKey(
            entity = MetaAccountLocal::class,
            parentColumns = ["id"],
            childColumns = ["metaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
class WalletCustodyLocal(
    @PrimaryKey val metaId: Long,
    val kind: String,
    val publicIdentitySha256: ByteArray
) {
    companion object {
        const val WATCH = "WATCH"
        const val SIGNED = "SIGNED"
    }
}
