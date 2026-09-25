package jp.co.soramitsu.common.base

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder

abstract class BaseActivity<T : BaseViewModel> : AppCompatActivity() {

    abstract val viewModel: T

    override fun attachBaseContext(base: Context) {
        val contextManager = ContextManager.getInstanceOrInit(base.applicationContext, LanguagesHolder())
        applyOverrideConfiguration(contextManager.setLocale(base).resources.configuration)
        super.attachBaseContext(contextManager.setLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Some activities guard sensitive startup work before their saved
        // FragmentManager state may be restored. Compute this using only
        // process-local state; Android APIs that require super.onCreate must
        // stay in onContentInitializationBlocked().
        val initializeContent = canInitializeContent()
        super.onCreate(savedInstanceState.takeIf { initializeContent })

        if (!initializeContent) {
            onContentInitializationBlocked()
            return
        }

        val decorView = window.decorView
        decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        setContentView(layoutResource())

        initViews()
        subscribe(viewModel)
    }

    protected open fun canInitializeContent(): Boolean = true

    protected open fun onContentInitializationBlocked() = Unit

    abstract fun layoutResource(): Int

    abstract fun initViews()

    abstract fun subscribe(viewModel: T)

    abstract fun changeLanguage()
}
