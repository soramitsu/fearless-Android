package jp.co.soramitsu.app.root.presentation

import android.content.Intent
import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.di.RootApi
import jp.co.soramitsu.app.root.di.RootComponent
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.common.base.BaseActivity
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.utils.showToast
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.common.view.dialog.infoDialog
import jp.co.soramitsu.common.view.dialog.retryDialog
import jp.co.soramitsu.splash.presentation.SplashBackgroundHolder
import kotlinx.android.synthetic.main.activity_root.mainView
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

        navigator.attach(navController, this)

        rootNetworkBar.setOnApplyWindowInsetsListener { view, insets ->
            view.updatePadding(top = insets.systemWindowInsetTop)

            insets
        }

        intent?.let(::processIntent)

//        processJsonOpenIntent()
    }

    override fun onDestroy() {
        super.onDestroy()

        navigator.detach()
    }

    override fun layoutResource(): Int {
        return R.layout.activity_root
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        processIntent(intent)
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
        viewModel.showConnectingBarLiveData.observe(this) { show ->
            rootNetworkBar.setVisible(show)
        }

        viewModel.messageLiveData.observe(
            this,
            EventObserver {
                showToast(it)
            }
        )

        viewModel.outdatedTypesWarningLiveData.observe(
            this,
            EventObserver {
                infoDialog(this) {
                    setTitle(R.string.runtime_update_not_actual_title)
                    setMessage(R.string.runtime_update_not_actual_description)
                }
            }
        )

        viewModel.runtimeUpdateFailedLiveData.observe(
            this,
            EventObserver { onRetry ->
                retryDialog(this, { viewModel.retryConfirmed(onRetry) }) {
                    setTitle(R.string.runtime_update_failure_title)
                    setMessage(R.string.runtime_update_failure_description)
                }
            }
        )
    }

    override fun removeSplashBackground() {
        mainView.setBackgroundResource(R.color.black)
    }

    override fun changeLanguage() {
        viewModel.noticeLanguageLanguage()

        recreate()

//        restartAfterLanguageChange(this)
    }

    private fun processIntent(intent: Intent) {
        val uri = intent.data?.toString()

        uri?.let { viewModel.externalUrlOpened(uri) }
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

    private val navController: NavController by lazy {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment

        navHostFragment.navController
    }
}
