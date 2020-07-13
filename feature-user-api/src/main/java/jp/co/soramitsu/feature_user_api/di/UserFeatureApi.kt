package jp.co.soramitsu.feature_user_api.di

import jp.co.soramitsu.feature_user_api.domain.interfaces.UserInteractor
import jp.co.soramitsu.feature_user_api.domain.interfaces.UserRepository

interface UserFeatureApi {

    fun provideUserRepository(): UserRepository

    fun provideUserInteractor(): UserInteractor
}