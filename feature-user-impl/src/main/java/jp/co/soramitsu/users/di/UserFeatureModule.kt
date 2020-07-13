package jp.co.soramitsu.users.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.feature_user_api.domain.interfaces.UserInteractor
import jp.co.soramitsu.feature_user_api.domain.interfaces.UserRepository
import jp.co.soramitsu.users.data.network.UserApi
import jp.co.soramitsu.users.data.network.UserApiImpl
import jp.co.soramitsu.users.data.repository.UserRepositoryImpl
import jp.co.soramitsu.users.domain.UsersInteractorImpl

@Module
class UserFeatureModule {

    @Provides
    @ApplicationScope
    fun provideUserRepository(userRepository: UserRepositoryImpl): UserRepository = userRepository

    @Provides
    @ApplicationScope
    fun provideUserInteractor(userInteractor: UsersInteractorImpl): UserInteractor = userInteractor

    @Provides
    @ApplicationScope
    fun provideUserApi(apiCreator: NetworkApiCreator): UserApi {
        return UserApiImpl()
    }
}