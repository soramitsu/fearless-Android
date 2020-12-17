package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.feature_wallet_api.domain.model.Token

class TokenConverters {

    @TypeConverter
    fun fromToken(type: Token.Type): Int {
        return type.ordinal
    }

    @TypeConverter
    fun toToken(ordinal: Int): Token.Type {
        return Token.Type.values()[ordinal]
    }
}