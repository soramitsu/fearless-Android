package jp.co.soramitsu.staking.api.di

import jp.co.soramitsu.staking.api.domain.api.StakingRepository

interface StakingFeatureApi {

    fun repository(): StakingRepository
}
