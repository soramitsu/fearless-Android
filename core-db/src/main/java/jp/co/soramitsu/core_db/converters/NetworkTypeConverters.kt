package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.feature_account_api.domain.model.Node

class NetworkTypeConverters {

    @TypeConverter
    fun fromNetworkType(networkType: Node.NetworkType): Int {
        return networkType.ordinal
    }

    @TypeConverter
    fun toNetworkType(ordinal: Int): Node.NetworkType {
        return Node.NetworkType.values()[ordinal]
    }
}