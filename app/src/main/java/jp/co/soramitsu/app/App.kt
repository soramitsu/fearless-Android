package jp.co.soramitsu.app

import android.app.Application
import jp.co.soramitsu.app.di.app.AppComponent
import jp.co.soramitsu.app.di.app.DaggerAppComponent
import jp.co.soramitsu.app.di.deps.ComponentDependenciesProvider
import jp.co.soramitsu.app.di.deps.FeatureHolderManager
import jp.co.soramitsu.app.di.deps.HasComponentDependencies
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.FeatureContainer
import javax.inject.Inject

open class App : Application(), FeatureContainer, HasComponentDependencies {

    @Inject lateinit var featureHolderManager: FeatureHolderManager

    @Inject
    override lateinit var dependencies: ComponentDependenciesProvider
        protected set

    private lateinit var appComponent: AppComponent

    override fun onCreate() {
        super.onCreate()

        appComponent = DaggerAppComponent
            .builder()
            .application(this)
            .build()
        appComponent.inject(this)
    }

    override fun <T> getFeature(key: Class<*>): T {
        return featureHolderManager.getFeature<T>(key)!!
    }

    override fun releaseFeature(key: Class<*>) {
        featureHolderManager.releaseFeature(key)
    }

    override fun commonApi(): CommonApi {
        return appComponent
    }
}