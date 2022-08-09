package jp.co.soramitsu.featurestakingimpl.domain.model

import java.math.BigInteger

sealed class NetworkInfo(
    open val lockupPeriodInDays: Int,
    open val minimumStake: BigInteger
) {
    data class RelayChain(
        override val lockupPeriodInDays: Int,
        override val minimumStake: BigInteger,
        val totalStake: BigInteger,
        val nominatorsCount: Int
    ) : NetworkInfo(lockupPeriodInDays, minimumStake)

    data class Parachain(
        override val lockupPeriodInDays: Int,
        override val minimumStake: BigInteger
    ) : NetworkInfo(lockupPeriodInDays, minimumStake)
}
