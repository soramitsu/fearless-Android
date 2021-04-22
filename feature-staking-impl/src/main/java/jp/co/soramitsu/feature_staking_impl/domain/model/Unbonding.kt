package jp.co.soramitsu.feature_staking_impl.domain.model

import java.math.BigInteger

class Unbonding(val amount: BigInteger, val daysLeft: Int)
