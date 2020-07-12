package jp.co.soramitsu.users.di

import jp.co.soramitsu.common.di.FeatureApiHolder
import jp.co.soramitsu.common.di.FeatureContainer
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.users.UsersRouter
import javax.inject.Inject

@ApplicationScope
class UserFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer,
    private val usersRouter: UsersRouter
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val userFeatureDependencies = DaggerUserFeatureComponent_UserFeatureDependenciesComponent.builder()
            .commonApi(commonApi())
            .dbApi(getFeature(DbApi::class.java))
            .build()
        return DaggerUserFeatureComponent.builder()
            .withDependencies(userFeatureDependencies)
            .router(usersRouter)
            .build()
    }
}