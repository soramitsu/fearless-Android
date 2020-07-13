package jp.co.soramitsu.app.di.deps

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import jp.co.soramitsu.app.di.app.AppComponent
import jp.co.soramitsu.app.di.main.MainDependencies

@Module
interface ComponentDependenciesModule {

    @Binds
    @IntoMap
    @ComponentDependenciesKey(MainDependencies::class)
    fun provideMainDependencies(component: AppComponent): ComponentDependencies
}