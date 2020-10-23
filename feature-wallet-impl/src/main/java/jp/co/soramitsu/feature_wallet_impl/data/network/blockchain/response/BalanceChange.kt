package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response

import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountInfo

class BalanceChange(
    val block: String,
    val newAccountInfo: EncodableStruct<AccountInfo>
)