package jp.co.soramitsu.staking.impl.domain.model

import java.math.BigInteger

sealed class NetworkInfo(
    open val lockupPeriodInHours: Int,
    open val minimumStake: BigInteger
) {
    data class RelayChain(
        override val lockupPeriodInHours: Int,
        override val minimumStake: BigInteger,
        val totalStake: BigInteger,
        val nominatorsCount: Int
    ) : NetworkInfo(lockupPeriodInHours, minimumStake)

    data class Parachain(
        override val lockupPeriodInHours: Int,
        override val minimumStake: BigInteger
    ) : NetworkInfo(lockupPeriodInHours, minimumStake)
}
