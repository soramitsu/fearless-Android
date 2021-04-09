package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct

class AccountInfoFactory(
    private val isUpgradedToTripleRefCount: SuspendableProperty<Boolean>
) {

    suspend fun decode(scale: String): EncodableStruct<AccountInfoSchema> {

        return if (isUpgradedToTripleRefCount.get()) {
            AccountInfoSchemaWithTripleRefCount.read(scale)
        } else {
            AccountInfoSchemaWithDualRefCount.read(scale)
        }
    }
}