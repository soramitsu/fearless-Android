package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core.model.CryptoType

class CryptoTypeConverters {

    @TypeConverter
    fun from(cryptoType: CryptoType): String = cryptoType.name

    @TypeConverter
    fun to(name: String): CryptoType = enumValueOf(name)
}
