package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct

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
}