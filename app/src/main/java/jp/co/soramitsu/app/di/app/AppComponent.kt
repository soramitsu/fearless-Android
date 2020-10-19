package jp.co.soramitsu.app.di.app

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.app.App
import jp.co.soramitsu.app.di.deps.ComponentHolderModule
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.modules.CommonModule
import jp.co.soramitsu.common.di.modules.NetworkModule
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.common.resources.ContextManager

@ApplicationScope
@Component(
    modules = [
        AppModule::class,
        CommonModule::class,
        NetworkModule::class,
        NavigationModule::class,
        ComponentHolderModule::class,
        FeatureManagerModule::class
    ]
)
interface AppComponent : CommonApi {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(application: App): Builder

        @BindsInstance
        fun contextManager(contextManager: ContextManager): Builder

        fun build(): AppComponent
    }

    fun inject(app: App)
}