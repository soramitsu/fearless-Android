package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.TransactionSource

class TransactionConverters {
    @TypeConverter
    fun fromTransactionSource(source: TransactionSource) = source.ordinal

    @TypeConverter
    fun toTransactionSource(ordinal: Int) = TransactionSource.values()[ordinal]
}