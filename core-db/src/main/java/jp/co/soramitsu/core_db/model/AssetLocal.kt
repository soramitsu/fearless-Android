package jp.co.soramitsu.core_db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.TypeConverter
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import java.math.BigDecimal
import java.math.BigInteger

@Entity(
    tableName = "assets",
    primaryKeys = ["token", "accountAddress"],
    foreignKeys = [ForeignKey(entity = AccountLocal::class,
        parentColumns = ["address"],
        childColumns = ["accountAddress"],
        onDelete = ForeignKey.CASCADE)]
)
class AssetLocal(
    val token: Asset.Token,
    @ColumnInfo(index = true) val accountAddress: String,
    val balanceInPlanks: BigInteger,
    val dollarRate: BigDecimal?,
    val recentRateChange: BigDecimal?
)

class AssetConverters {

    @TypeConverter
    fun fromToken(token: Asset.Token): Int {
        return token.ordinal
    }

    @TypeConverter
    fun toToken(ordinal: Int): Asset.Token {
        return Asset.Token.values()[ordinal]
    }

    @TypeConverter
    fun fromBigDecimal(balance: BigDecimal?): String? {
        return balance?.toString()
    }

    @TypeConverter
    fun toBigDecimal(balance: String?): BigDecimal? {
        return balance?.let { BigDecimal(it) }
    }

    @TypeConverter
    fun fromBigInteger(balance: BigInteger): String {
        return balance.toString()
    }

    @TypeConverter
    fun toBigInteger(balance: String): BigInteger {
        return BigInteger(balance)
    }
}