package jp.co.soramitsu.feature_wallet_impl.data.repository

import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.feature_wallet_api.domain.model.calculateTotalBalance
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountData
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountInfoSchema

fun EncodableStruct<AccountInfoSchema>.totalBalanceInPlanks(): BigInteger {
    val accountData = this[schema.data]

    return calculateTotalBalance(
        freeInPlanks = accountData[AccountData.free],
        reservedInPlanks = accountData[AccountData.reserved]
    )
}
