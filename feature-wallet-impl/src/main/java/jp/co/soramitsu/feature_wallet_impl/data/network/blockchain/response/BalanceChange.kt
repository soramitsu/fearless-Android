package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response

import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountInfoSchema

class BalanceChange(
    val block: String,
    val newAccountInfo: EncodableStruct<AccountInfoSchema>
)
