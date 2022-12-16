package jp.co.soramitsu.common.base

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.CustomSnackbarType
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder


abstract class BaseActivity<T : BaseViewModel> : AppCompatActivity() {

    abstract val viewModel: T

    abstract fun layoutResource(): Int

    abstract fun initViews()

    abstract fun subscribe(viewModel: T)

    abstract fun changeLanguage()

    override fun attachBaseContext(base: Context) {
        val contextManager = ContextManager.getInstanceOrInit(base.applicationContext, LanguagesHolder())
        applyOverrideConfiguration(contextManager.setLocale(base).resources.configuration)
        super.attachBaseContext(contextManager.setLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val decorView = window.decorView
        decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )

        setContentView(layoutResource())

        initViews()
        subscribe(viewModel)
    }

    abstract fun getRootView(): View?

    fun showSnackbar(type: CustomSnackbarType) {
//        val baseFragment = findSnackbarOwner()
//        baseFragment?.showSnackbar(type)

        val rootView = getRootView()
        makeSnackbarAndShow(type)
    }

    fun makeSnackbarAndShow(type: CustomSnackbarType) {
        getRootView()?.let { rootView ->
            val snackbar = Snackbar.make(rootView, "", Snackbar.LENGTH_SHORT)
            val layout = snackbar.view as Snackbar.SnackbarLayout
            val textView = layout.findViewById<View>(R.id.snackbar_text) as TextView
            textView.visibility = View.INVISIBLE

            val snackView: View = layoutInflater.inflate(R.layout.custom_snackbar, null)

            snackView.backgroundTintList = ColorStateList.valueOf(type.color.toArgb())
            val imageView: ImageView = snackView.findViewById<View>(R.id.icon) as ImageView
            imageView.setImageDrawable(AppCompatResources.getDrawable(this, type.iconRes))
            val titleView = snackView.findViewById<View>(R.id.title) as TextView
            val descriptionView = snackView.findViewById<View>(R.id.description) as TextView

            titleView.text = getString(type.titleRes)
            descriptionView.isVisible = type.descriptionRes != null
            type.descriptionRes?.let { descriptionView.text = getString(it) }
//If the view is not covering the whole snackbar layout, add this line
            layout.setPadding(0, 0, 0, 0)
// Add the view to the Snackbar's layout
            layout.addView(snackView, 0)
            snackbar.show()
        }
    }

    private fun findSnackbarOwner(fragments: List<Fragment> = supportFragmentManager.fragments): SnackbarShowerInterface? {
        fragments.forEach {
            when {
                it is SnackbarOwnerInterface -> return it
                it is SnackbarShowerInterface -> Unit
                it.childFragmentManager.fragments.isNotEmpty() -> {
                    return findSnackbarOwner(it.childFragmentManager.fragments)
                }
            }
        }
        return null
    }
}
