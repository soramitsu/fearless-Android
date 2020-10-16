package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response

import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountInfo
import java.math.BigInteger

class FeeResponse(
    val feeRemote: FeeRemote,
    val newAccountInfo: EncodableStruct<AccountInfo>
)

class FeeRemote(
    val partialFee: BigInteger,
    val weight: Long
)