package jp.co.soramitsu.feature_staking_impl.domain.alerts

import java.math.BigDecimal
import jp.co.soramitsu.feature_staking_api.domain.model.Delegation
import jp.co.soramitsu.feature_wallet_api.domain.model.Token

sealed class Alert {

    class RedeemTokens(val amount: BigDecimal, val token: Token) : Alert()

    class BondMoreTokens(val minimalStake: BigDecimal, val token: Token) : Alert()

    object ChangeValidators : Alert()

    object WaitingForNextEra : Alert()

    object SetValidators : Alert()

    class ChangeCollators(val collatorIdHex: String, delegation: Delegation) : Alert()
}
