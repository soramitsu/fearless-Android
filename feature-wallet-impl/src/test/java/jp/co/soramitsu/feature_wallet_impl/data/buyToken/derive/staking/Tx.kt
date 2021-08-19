package jp.co.soramitsu.feature_wallet_impl.data.buyToken.derive.staking

import jp.co.soramitsu.common.data.network.runtime.binding.BalanceOf
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.tx.DecoratableFunctions
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.tx.DecoratableTx
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.tx.Function0
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.tx.Function1

interface StakingFunctions : DecoratableFunctions

val DecoratableTx.staking: StakingFunctions
    get() = decorate("Staking") {
        object : StakingFunctions, DecoratableFunctions by this {}
    }

val StakingFunctions.chill: Function0
    get() = decorator.function0("chill")

val StakingFunctions.unbond: Function1<BalanceOf>
    get() = decorator.function1("unbond")
