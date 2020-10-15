package jp.co.soramitsu.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import jp.co.soramitsu.app.di.app.AppComponent
import jp.co.soramitsu.app.di.app.DaggerAppComponent
import jp.co.soramitsu.app.di.deps.FeatureHolderManager
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.FeatureContainer
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import javax.inject.Inject

open class App : Application(), FeatureContainer {

    @Inject lateinit var featureHolderManager: FeatureHolderManager

    private lateinit var appComponent: AppComponent

    private val languagesHolder: LanguagesHolder = LanguagesHolder()

    override fun attachBaseContext(base: Context) {
        val contextManager = ContextManager.getInstanceOrInit(base, languagesHolder)
        super.attachBaseContext(contextManager.setLocale(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        val contextManager = ContextManager.getInstanceOrInit(this, languagesHolder)
        contextManager.setLocale(this)
    }

    override fun onCreate() {
        super.onCreate()
        val contextManger = ContextManager.getInstanceOrInit(this, languagesHolder)

        appComponent = DaggerAppComponent
            .builder()
            .application(this)
            .contextManager(contextManger)
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