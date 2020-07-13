package jp.co.soramitsu.app.di.app

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.app.App
import jp.co.soramitsu.app.di.deps.ComponentDependenciesModule
import jp.co.soramitsu.app.di.deps.ComponentHolderModule
import jp.co.soramitsu.app.di.main.MainDependencies
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.modules.CommonModule
import jp.co.soramitsu.common.di.modules.NetworkModule
import jp.co.soramitsu.common.di.scope.ApplicationScope

@ApplicationScope
@Component(
    modules = [
        AppModule::class,
        CommonModule::class,
        NetworkModule::class,
        NavigationModule::class,
        ComponentHolderModule::class,
        ComponentDependenciesModule::class,
        FeatureManagerModule::class
    ]
)
interface AppComponent : MainDependencies, CommonApi {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(application: App): Builder

        fun build(): AppComponent
    }

    fun inject(app: App)
}