package jp.co.soramitsu.wallet.impl.data.repository

import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.OrmlTokensAccountData
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.wallet.impl.domain.model.calculateTotalBalance
import java.math.BigInteger

val AccountInfo.totalBalance: BigInteger
    get() = calculateTotalBalance(
        freeInPlanks = data.free,
        reservedInPlanks = data.reserved
    ).orZero()

val OrmlTokensAccountData.totalBalance: BigInteger
    get() = calculateTotalBalance(
        freeInPlanks = free,
        reservedInPlanks = reserved
    ).orZero()
