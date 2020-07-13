package jp.co.soramitsu.users.di

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_user_api.di.UserFeatureApi
import jp.co.soramitsu.users.UsersRouter
import jp.co.soramitsu.users.presentation.details.di.UserComponent
import jp.co.soramitsu.users.presentation.list.di.UsersComponent

@ApplicationScope
@Component(
    dependencies = [
        UserFeatureDependencies::class
    ],
    modules = [
        UserFeatureModule::class
    ]
)
interface UserFeatureComponent : UserFeatureApi {

    fun usersComponentFactory(): UsersComponent.Factory

    fun userComponentFactory(): UserComponent.Factory

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun router(usersRouter: UsersRouter): Builder

        fun withDependencies(deps: UserFeatureDependencies): Builder

        fun build(): UserFeatureComponent
    }

    @Component(
        dependencies = [
            CommonApi::class,
            DbApi::class
        ]
    )
    interface UserFeatureDependenciesComponent : UserFeatureDependencies
}