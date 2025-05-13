package jp.co.soramitsu.coredb.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core.models.CryptoType

class CryptoTypeConverters {

    @TypeConverter
    fun from(cryptoType: CryptoType?): String? = cryptoType?.name

    @TypeConverter
    fun to(name: String?): CryptoType? = kotlin.runCatching { name?.let { enumValueOf<CryptoType>(it) } }.getOrNull()
}
