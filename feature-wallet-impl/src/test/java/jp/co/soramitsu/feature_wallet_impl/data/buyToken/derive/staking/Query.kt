package jp.co.soramitsu.feature_wallet_impl.data.buyToken.derive.staking

import jp.co.soramitsu.common.data.network.runtime.binding.bindAccountId
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.query.DecoratableQuery
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.query.DecoratableStorage

interface StakingStorage : DecoratableStorage

val DecoratableQuery.staking: StakingStorage
    get() = decorate("Staking") {
        object : StakingStorage, DecoratableStorage by this {}
    }

val StakingStorage.historyDepth
    get() = decorator.plain("HistoryDepth", ::bindNumber)

val StakingStorage.bonded
    get() = decorator.single<AccountId, AccountId>("Bonded", ::bindAccountId)
