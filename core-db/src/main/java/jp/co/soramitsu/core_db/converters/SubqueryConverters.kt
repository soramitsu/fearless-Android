package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.SubqueryHistoryModel

class SubqueryConverters {
    @TypeConverter
    fun fromTransactionSource(source: SubqueryHistoryModel.Source) = source.ordinal

    @TypeConverter
    fun toTransactionSource(ordinal: Int) = SubqueryHistoryModel.Source.values()[ordinal]

    @TypeConverter
    fun fromTransactionStatus(status: SubqueryHistoryModel.Status) = status.ordinal

    @TypeConverter
    fun toTransactionStatus(ordinal: Int) = SubqueryHistoryModel.Status.values()[ordinal]
}
