package jp.co.soramitsu.common.base

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import jp.co.soramitsu.common.di.FeatureContainer
import jp.co.soramitsu.common.utils.setBarColorBackground
import javax.inject.Inject

abstract class BaseActivity<T : BaseViewModel> : AppCompatActivity() {

    @Inject protected open lateinit var viewModel: T

    override fun attachBaseContext(base: Context) {
        val commonApi = (base.applicationContext as FeatureContainer).commonApi()
        val contextManager = commonApi.contextManager()
        applyOverrideConfiguration(contextManager.setLocale(base).resources.configuration)
        super.attachBaseContext(contextManager.setLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layoutResource())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setBarColorBackground(android.R.color.black)
        }

        inject()
        initViews()
        subscribe(viewModel)
    }

    abstract fun inject()

    abstract fun layoutResource(): Int

    abstract fun initViews()

    abstract fun subscribe(viewModel: T)

    open fun changeLanguage() {
    }
}