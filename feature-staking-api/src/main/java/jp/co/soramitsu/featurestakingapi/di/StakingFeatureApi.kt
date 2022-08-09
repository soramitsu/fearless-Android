package jp.co.soramitsu.featurestakingapi.di

import jp.co.soramitsu.featurestakingapi.domain.api.StakingRepository

interface StakingFeatureApi {

    fun repository(): StakingRepository
}
