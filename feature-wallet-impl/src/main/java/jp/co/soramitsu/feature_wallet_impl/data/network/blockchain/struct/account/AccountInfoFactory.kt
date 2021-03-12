package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.invoke

class AccountInfoFactory(
    val isUpgradedToDualRefCount: SuspendableProperty<Boolean>
) {

    suspend fun decode(scale: String): EncodableStruct<AccountInfoSchema> {

        return if (isUpgradedToDualRefCount.get()) {
            AccountInfoSchemaV28.read(scale)
        } else {
            AccountInfoSchemaV27.read(scale)
        }
    }

    fun createEmpty(): EncodableStruct<AccountInfoSchema> = AccountInfoSchemaV28 { info ->
        info[AccountInfoSchemaV28.nonce] = 0.toUInt()
        info[AccountInfoSchemaV28.providers] = 0.toUInt()
        info[AccountInfoSchemaV28.consumers] = 0.toUInt()

        info[AccountInfoSchemaV28.data] = AccountData { data ->
            data[AccountData.free] = 0.toBigInteger()
            data[AccountData.reserved] = 0.toBigInteger()
            data[AccountData.miscFrozen] = 0.toBigInteger()
            data[AccountData.feeFrozen] = 0.toBigInteger()
        }
    }
}
