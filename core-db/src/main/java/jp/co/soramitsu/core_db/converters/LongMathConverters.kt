package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.math.BigInteger

class LongMathConverters {

    @TypeConverter
    fun fromBigDecimal(balance: BigDecimal?): String? {
        return balance?.toString()
    }

    @TypeConverter
    fun toBigDecimal(balance: String?): BigDecimal? {
        return balance?.let { BigDecimal(it) }
    }

    @TypeConverter
    fun fromBigInteger(balance: BigInteger?): String? {
        return balance?.toString()
    }

    @TypeConverter
    fun toBigInteger(balance: String?): BigInteger? {
        return balance?.let { BigInteger(it) }
    }
}