package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.TransactionSource
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction

class TransactionConverters {
    @TypeConverter
    fun fromTransactionSource(source: TransactionSource) = source.ordinal

    @TypeConverter
    fun toTransactionSource(ordinal: Int) = TransactionSource.values()[ordinal]

    @TypeConverter
    fun fromTransactionStatus(status: Transaction.Status) = status.ordinal

    @TypeConverter
    fun toTransactionStatus(ordinal: Int) = Transaction.Status.values()[ordinal]
}