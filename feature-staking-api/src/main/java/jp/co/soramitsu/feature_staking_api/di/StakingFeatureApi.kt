package jp.co.soramitsu.feature_staking_api.di

interface StakingFeatureApi {

    fun provideUpdaters(): StakingUpdaters
}
