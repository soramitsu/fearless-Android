package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.TokenLocal

class TokenConverters {

    @TypeConverter
    fun fromToken(type: TokenLocal.Type): Int {
        return type.ordinal
    }

    @TypeConverter
    fun toToken(ordinal: Int): TokenLocal.Type {
        return TokenLocal.Type.values()[ordinal]
    }
}