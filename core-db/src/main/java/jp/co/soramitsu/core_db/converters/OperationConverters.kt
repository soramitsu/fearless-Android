package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.OperationLocal

class OperationConverters {
    @TypeConverter
    fun fromOperationSource(source: OperationLocal.Source) = source.ordinal

    @TypeConverter
    fun toOperationSource(ordinal: Int) = OperationLocal.Source.values()[ordinal]

    @TypeConverter
    fun fromOperationStatus(status: OperationLocal.Status) = status.ordinal

    @TypeConverter
    fun toOperationStatus(ordinal: Int) = OperationLocal.Status.values()[ordinal]

    @TypeConverter
    fun fromOperationType(type: OperationLocal.Type) = type.ordinal

    @TypeConverter
    fun toOperationType(ordinal: Int) = OperationLocal.Type.values()[ordinal]
}
