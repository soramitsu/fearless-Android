package jp.co.soramitsu.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import dagger.hilt.android.HiltAndroidApp
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder

@HiltAndroidApp
open class App : Application() {

    private val languagesHolder: LanguagesHolder = LanguagesHolder()

    override fun attachBaseContext(base: Context) {
        val contextManager = ContextManager.getInstanceOrInit(base, languagesHolder)
        super.attachBaseContext(contextManager.setLocale(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val contextManager = ContextManager.getInstanceOrInit(this, languagesHolder)
        contextManager.setLocale(this)
    }
}
