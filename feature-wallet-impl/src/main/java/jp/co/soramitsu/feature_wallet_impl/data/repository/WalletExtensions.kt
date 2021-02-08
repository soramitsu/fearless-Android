package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.common.utils.sumBy
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.feature_wallet_api.domain.model.calculateTotalBalance
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.StakingLedger
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.UnlockChunk
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountData
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountInfoSchema
import java.math.BigInteger

fun EncodableStruct<StakingLedger>.sumStaking(
    condition: (chunkEra: Long) -> Boolean
): BigInteger {

    return this[StakingLedger.unlocking]
        .filter { condition(it[UnlockChunk.era].toLong()) }
        .sumBy { it[UnlockChunk.value] }
}

fun EncodableStruct<AccountInfoSchema>.totalBalanceInPlanks(): BigInteger {
    val accountData = this[schema.data]

    return calculateTotalBalance(
        freeInPlanks = accountData[AccountData.free],
        reservedInPlanks = accountData[AccountData.reserved]
    )
}