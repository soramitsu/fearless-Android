package jp.co.soramitsu.app.root.presentation

import android.os.Bundle
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.di.RootApi
import jp.co.soramitsu.app.root.di.RootComponent
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.common.base.BaseActivity
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.splash.presentation.SplashBackgroundHolder
import kotlinx.android.synthetic.main.activity_root.mainView
import kotlinx.android.synthetic.main.activity_root.navHost
import kotlinx.android.synthetic.main.activity_root.rootNetworkBar
import javax.inject.Inject

class RootActivity : BaseActivity<RootViewModel>(), SplashBackgroundHolder {

    @Inject
    lateinit var navigator: Navigator

    override fun inject() {
        FeatureUtils.getFeature<RootComponent>(this, RootApi::class.java)
            .mainActivityComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        removeSplashBackground()

        viewModel.restoredAfterConfigChange()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        navController.setGraph(R.navigation.root_nav_graph)
        navigator.attachNavController(navController)

        rootNetworkBar.setOnApplyWindowInsetsListener { view, insets ->
            view.updatePadding(top = insets.systemWindowInsetTop)

            insets
        }

//        processJsonOpenIntent()
    }

    override fun layoutResource(): Int {
        return R.layout.activity_root
    }

    override fun initViews() {
    }

    override fun onStop() {
        super.onStop()

        viewModel.noticeInBackground()
    }

    override fun onStart() {
        super.onStart()

        viewModel.noticeInForeground()
    }

    override fun subscribe(viewModel: RootViewModel) {
        viewModel.showConnectingBarLiveData.observe(this, Observer { show ->
            rootNetworkBar.setVisible(show)
        })
    }

    override fun removeSplashBackground() {
        mainView.setBackgroundResource(R.color.black)
    }

    override fun changeLanguage() {
        viewModel.noticeLanguageLanguage()

        recreate()

//        restartAfterLanguageChange(this)
    }

//    private fun processJsonOpenIntent() {
//        if (Intent.ACTION_VIEW == intent.action && intent.type != null) {
//            if ("application/json" == intent.type) {
//                val file = this.contentResolver.openInputStream(intent.data!!)
//                val content = file?.reader(Charsets.UTF_8)?.readText()
//                viewModel.jsonFileOpened(content)
//            }
//        }
//    }

    private val navController: NavController
        get() = NavHostFragment.findNavController(navHost)
}