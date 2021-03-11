package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account

import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Field
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.uint128
import jp.co.soramitsu.fearless_utils.scale.uint32

abstract class AccountInfoSchema : Schema<AccountInfoSchema>() {
    abstract val nonce: Field<UInt>

    abstract val data: Field<EncodableStruct<AccountData>>
}

object AccountData : Schema<AccountData>() {
    val free by uint128()
    val reserved by uint128()
    val miscFrozen by uint128()
    val feeFrozen by uint128()
}

object AccountInfoSchemaV27 : AccountInfoSchema() {
    override val nonce by uint32()

    val refCount by uint32()

    override val data by schema(AccountData)
}

object AccountInfoSchemaV28 : AccountInfoSchema() {
    override val nonce by uint32()

    val consumers by uint32()
    val providers by uint32()

    override val data by schema(AccountData)
}