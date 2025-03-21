package jp.co.soramitsu.app.root.presentation

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.InitializationStep
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.common.PLAY_MARKET_APP_URI
import jp.co.soramitsu.common.PLAY_MARKET_BROWSER_URI
import jp.co.soramitsu.common.base.BaseActivity
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.common.utils.observe
import jp.co.soramitsu.common.utils.showToast
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import jp.co.soramitsu.runtime.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class RootActivity : BaseActivity<RootViewModel>(), LifecycleObserver {

    companion object {
        private const val ANIM_DURATION = 150L
        private const val ANIM_START_POSITION = 100f
    }

    @Inject
    lateinit var navigator: Navigator

    override val viewModel: RootViewModel by viewModels()
    private var animation: Animation? = null

    private val rootNetworkBar: TextView by lazy { findViewById(R.id.rootNetworkBar) }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        viewModel.restoredAfterConfigChange()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        navigator.attach(navController, this)

        rootNetworkBar.setOnApplyWindowInsetsListener { view, insets ->
            view.updatePadding(
                top = WindowInsetsCompat.toWindowInsetsCompat(insets, view)
                    .getInsets(WindowInsetsCompat.Type.systemBars()).top
            )

            insets
        }

        intent?.let(::processIntent)

        subscribeNetworkStatus()
    }

    private fun subscribeNetworkStatus() {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                viewModel.onNetworkAvailable()
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                viewModel.onConnectionLost()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val hasInternetCapability =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (hasInternetCapability) {
                    viewModel.onNetworkAvailable()
                } else {
                    viewModel.onConnectionLost()
                }
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        val connectivityManager =
            getSystemService(ConnectivityManager::class.java) as ConnectivityManager
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        lifecycleScope.launch {
            while (isActive) {
                delay(5_000)
                val isConnected = checkNetworkStatus(connectivityManager)
                val hasInternetAccess = hasInternetAccess()
                withContext(Dispatchers.Main){
                    if(isConnected || hasInternetAccess){
                        viewModel.onNetworkAvailable()
                    } else {
                        viewModel.onConnectionLost()
                    }
                }
            }
        }
    }

    private suspend fun hasInternetAccess(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(BuildConfig.CHAINS_URL)
                val urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.connectTimeout = 1000
                urlConnection.connect()
                urlConnection.responseCode == 200
            } catch (e: IOException) {
                false
            }
        }
    }

    private fun checkNetworkStatus(connectivityManager: ConnectivityManager): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    override fun onDestroy() {
        super.onDestroy()

        animation?.cancel()
        animation = null

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
        viewModel.showConnectingBar.observe(lifecycleScope) { show ->
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
                showNoInternetConnectionAlert(it)
            }
        )
    }

    private fun showUnsupportedAppVersionAlert() {
        AlertBottomSheet.Builder(this)
            .setTitle(R.string.update_needed_text)
            .setMessage(R.string.chain_unsupported_text)
            .setButtonText(R.string.common_update)
            .setCancelable(false)
            .callback { viewModel.updateAppClicked() }
            .build()
            .show()
    }

    private fun showNoInternetConnectionAlert(initializationStep: InitializationStep) {
        AlertBottomSheet.Builder(this)
            .setTitle(R.string.common_connection_problems)
            .setMessage(R.string.connection_problems_alert_message)
            .setButtonText(R.string.common_retry)
            .setCancelable(false)
            .callback { viewModel.retryLoadConfigClicked(initializationStep) }
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
        animation?.cancel()
        if (rootNetworkBar.isVisible) {
            return
        }

        val errorColor = getColor(R.color.colorAccent)
        findViewById<TextView>(R.id.rootNetworkBar).apply {
            setText(R.string.network_status_connecting)
            setBackgroundColor(errorColor)
        }
        animation = TranslateAnimation(0f, 0f, -ANIM_START_POSITION, 0f).apply {
            duration = ANIM_DURATION
            findViewById<TextView>(R.id.rootNetworkBar).startAnimation(this)
        }
        rootNetworkBar.isVisible = true
    }

    private fun hideBadConnectionView() {
        animation?.cancel()
        if (!rootNetworkBar.isVisible) {
            return
        }

        val successColor = getColor(R.color.green)
        rootNetworkBar.setText(R.string.network_status_connected)
        rootNetworkBar.setBackgroundColor(successColor)
        animation = TranslateAnimation(0f, 0f, 0f, -ANIM_START_POSITION).apply {
            duration = ANIM_DURATION
            startOffset = 500
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationRepeat(p0: Animation?) {
                }

                override fun onAnimationEnd(p0: Animation?) {
                    findViewById<TextView>(R.id.rootNetworkBar).isVisible = false
                }

                override fun onAnimationStart(p0: Animation?) {
                }
            })
            rootNetworkBar.startAnimation(this)
        }
    }

    override fun changeLanguage() {
        viewModel.noticeLanguageLanguage()

        recreate()
    }

    private fun processIntent(intent: Intent) {
        val uri = intent.data?.toString()

        uri?.let { viewModel.externalUrlOpened(uri) }
    }

    private val navController: NavController by lazy {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment

        navHostFragment.navController
    }
}
