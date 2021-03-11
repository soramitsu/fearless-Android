package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.TransactionLocal

class TransactionConverters {
    @TypeConverter
    fun fromTransactionSource(source: TransactionLocal.Source) = source.ordinal

    @TypeConverter
    fun toTransactionSource(ordinal: Int) = TransactionLocal.Source.values()[ordinal]

    @TypeConverter
    fun fromTransactionStatus(status: TransactionLocal.Status) = status.ordinal

    @TypeConverter
    fun toTransactionStatus(ordinal: Int) = TransactionLocal.Status.values()[ordinal]
}