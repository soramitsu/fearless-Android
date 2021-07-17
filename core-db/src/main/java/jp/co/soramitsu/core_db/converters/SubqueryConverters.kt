package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.OperationLocal

class SubqueryConverters {
    @TypeConverter
    fun fromTransactionSource(source: OperationLocal.Source) = source.ordinal

    @TypeConverter
    fun toTransactionSource(ordinal: Int) = OperationLocal.Source.values()[ordinal]

    @TypeConverter
    fun fromTransactionStatus(status: OperationLocal.Status) = status.ordinal

    @TypeConverter
    fun toTransactionStatus(ordinal: Int) = OperationLocal.Status.values()[ordinal]
}
