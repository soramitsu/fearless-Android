package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.feature_wallet_api.domain.model.calculateTotalBalance
import java.math.BigInteger

val AccountInfo.totalBalance: BigInteger
    get() = calculateTotalBalance(
        freeInPlanks = data.free,
        reservedInPlanks = data.reserved
    )
