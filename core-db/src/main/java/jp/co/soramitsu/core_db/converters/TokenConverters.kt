package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset

class TokenConverters {

    @TypeConverter
    fun fromToken(token: Asset.Token): Int {
        return token.ordinal
    }

    @TypeConverter
    fun toToken(ordinal: Int): Asset.Token {
        return Asset.Token.values()[ordinal]
    }
}