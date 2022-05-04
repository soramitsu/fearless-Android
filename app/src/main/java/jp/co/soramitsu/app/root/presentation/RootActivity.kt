package jp.co.soramitsu.app.root.presentation

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.di.RootApi
import jp.co.soramitsu.app.root.di.RootComponent
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.common.PLAY_MARKET_APP_URI
import jp.co.soramitsu.common.PLAY_MARKET_BROWSER_URI
import jp.co.soramitsu.common.base.BaseActivity
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.common.utils.showToast
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import kotlinx.android.synthetic.main.activity_root.rootNetworkBar
import javax.inject.Inject

class RootActivity : BaseActivity<RootViewModel>(), LifecycleObserver {

    companion object {
        private const val ANIM_DURATION = 150L
        private const val ANIM_START_POSITION = 100f
    }

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

        viewModel.restoredAfterConfigChange()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        navigator.attach(navController, this)

        rootNetworkBar.setOnApplyWindowInsetsListener { view, insets ->
            view.updatePadding(top = WindowInsetsCompat.toWindowInsetsCompat(insets, view).getInsets(WindowInsetsCompat.Type.systemBars()).top)

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

    @SuppressLint("ClickableViewAccessibility")
    override fun initViews() {
        findViewById<View>(R.id.root_touch_interceptor).setOnTouchListener { v, event ->
            viewModel.onUserInteractedWithApp()
            false
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onMoveToBackground() {
        viewModel.noticeInBackground()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onMoveToForeground() {
        viewModel.noticeInForeground()
    }

    override fun subscribe(viewModel: RootViewModel) {
        viewModel.showConnectingBarLiveData.observe(this) { show ->
            when {
                show -> showBadConnectionView()
                else -> hideBadConnectionView()
            }
        }

        viewModel.messageLiveData.observe(
            this,
            EventObserver {
                showToast(it)
            }
        )

        viewModel.showUnsupportedAppVersionAlert.observe(
            this,
            EventObserver {
                showUnsupportedAppVersionAlert()
            }
        )
        viewModel.openPlayMarket.observe(
            this,
            EventObserver {
                openPlayMarket()
            }
        )
        viewModel.closeApp.observe(
            this,
            EventObserver {
                finish()
            }
        )
        viewModel.showNoInternetConnectionAlert.observe(
            this,
            EventObserver {
                showNoInternetConnectionAlert()
            }
        )
    }

    private fun showUnsupportedAppVersionAlert() {
        AlertBottomSheet.Builder(this)
            .setTitle(R.string.update_needed_text)
            .setMessage(R.string.chain_unsupported_text)
            .setButtonText(jp.co.soramitsu.feature_wallet_impl.R.string.common_update)
            .setCancelable(false)
            .callback { viewModel.updateAppClicked() }
            .build()
            .show()
    }

    private fun showNoInternetConnectionAlert() {
        AlertBottomSheet.Builder(this)
            .setTitle(jp.co.soramitsu.feature_wallet_impl.R.string.common_connection_problems)
            .setMessage(jp.co.soramitsu.feature_wallet_impl.R.string.connection_problems_alert_message)
            .setButtonText(jp.co.soramitsu.feature_wallet_impl.R.string.common_retry)
            .setCancelable(false)
            .callback { viewModel.retryLoadConfigClicked() }
            .build()
            .show()
    }

    private fun openPlayMarket() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_MARKET_APP_URI)))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_MARKET_BROWSER_URI)))
        }
    }

    private fun showBadConnectionView() {
        if (rootNetworkBar.isVisible) {
            return
        }

        val errorColor = getColor(R.color.colorAccent)
        rootNetworkBar.setText(R.string.network_status_connecting)
        rootNetworkBar.setBackgroundColor(errorColor)
        val animation = TranslateAnimation(0f, 0f, -ANIM_START_POSITION, 0f)
        animation.duration = ANIM_DURATION
        rootNetworkBar.startAnimation(animation)
        rootNetworkBar.isVisible = true
    }

    private fun hideBadConnectionView() {
        if (!rootNetworkBar.isVisible) {
            return
        }

        val successColor = getColor(R.color.green)
        rootNetworkBar.setText(R.string.network_status_connected)
        rootNetworkBar.setBackgroundColor(successColor)
        val animation = TranslateAnimation(0f, 0f, 0f, -ANIM_START_POSITION)
        animation.duration = ANIM_DURATION
        animation.startOffset = 500
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationRepeat(p0: Animation?) {
            }

            override fun onAnimationEnd(p0: Animation?) {
                rootNetworkBar.isVisible = false
            }

            override fun onAnimationStart(p0: Animation?) {
            }
        })
        rootNetworkBar.startAnimation(animation)
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
