package jp.co.soramitsu.feature_staking_api.di

import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository

interface StakingFeatureApi {

    fun repository(): StakingRepository
}
