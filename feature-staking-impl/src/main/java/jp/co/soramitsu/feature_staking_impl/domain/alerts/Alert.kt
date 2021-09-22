package jp.co.soramitsu.feature_staking_impl.domain.alerts

import java.math.BigDecimal

sealed class Alert {

    class RedeemTokens(val amount: BigDecimal, val token: Token) : Alert()

    class BondMoreTokens(val minimalStake: BigDecimal, val token: Token) : Alert()

    object ChangeValidators : Alert()

    object WaitingForNextEra : Alert()

    object SetValidators : Alert()
}
