package jp.co.soramitsu.feature_wallet_impl.data.network.struct

import jp.co.soramitsu.common.data.network.scale.Schema
import jp.co.soramitsu.common.data.network.scale.schema
import jp.co.soramitsu.common.data.network.scale.uint128
import jp.co.soramitsu.common.data.network.scale.uint32

object AccountData : Schema<AccountData>() {
    val free by uint128()
    val reserved by uint128()
    val miscFrozen by uint128()
    val feeFrozen by uint128()
}

object AccountInfo : Schema<AccountInfo>() {
    val nonce by uint32()

    val refCount by uint32()

    val data by schema(AccountData)
}