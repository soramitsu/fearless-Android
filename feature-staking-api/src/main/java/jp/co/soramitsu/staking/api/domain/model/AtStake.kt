package jp.co.soramitsu.staking.api.domain.model

import java.math.BigInteger

class AtStake(
    val bond: BigInteger,
    val total: BigInteger,
    val delegations: List<Pair<ByteArray, BigInteger>>
)
