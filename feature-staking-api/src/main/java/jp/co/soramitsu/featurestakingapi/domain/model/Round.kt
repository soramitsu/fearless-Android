package jp.co.soramitsu.featurestakingapi.domain.model

import java.math.BigInteger

data class Round(
    val current: BigInteger,
    val first: BigInteger,
    val length: BigInteger
)
