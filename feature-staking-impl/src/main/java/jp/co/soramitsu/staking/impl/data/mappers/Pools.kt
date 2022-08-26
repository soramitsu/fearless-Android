package jp.co.soramitsu.staking.impl.data.mappers

import jp.co.soramitsu.staking.impl.data.model.PoolMember
import jp.co.soramitsu.staking.impl.data.model.PoolUnbonding

fun PoolMember.toDomain(): jp.co.soramitsu.staking.api.domain.model.PoolMember {
    return jp.co.soramitsu.staking.api.domain.model.PoolMember(
        poolId, points, lastRecordedRewardCounter, unbondingEras.toDomain()
    )
}

fun List<PoolUnbonding>.toDomain(): List<jp.co.soramitsu.staking.api.domain.model.PoolUnbonding> {
    return map { it.toDomain() }
}

fun PoolUnbonding.toDomain(): jp.co.soramitsu.staking.api.domain.model.PoolUnbonding {
    return jp.co.soramitsu.staking.api.domain.model.PoolUnbonding(
        era,
        amount
    )
}
