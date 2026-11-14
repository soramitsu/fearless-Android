package jp.co.soramitsu.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import jp.co.soramitsu.app.root.domain.SharedExportCleanup
import jp.co.soramitsu.app.root.domain.SoraCardLeftoverCleanup
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.OptionsProvider
import jp.co.soramitsu.common.data.network.config.MutationAuthorizationRefresher
import jp.co.soramitsu.common.feedback.TransferFeedback
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.utils.initializeTonAddressCode
import jp.co.soramitsu.walletconnect.impl.data.WalletConnectStarter

/**
 * Application entry point.
 *
 * - Boots Hilt DI via `@HiltAndroidApp`.
 * - Applies current locale using `ContextManager` and `LanguagesHolder`.
 * - Publishes build metadata (version, build type) through `OptionsProvider`.
 * - Deletes storage left by the removed SORA Card feature (`SoraCardLeftoverCleanup`).
 * - Starts the in-repo WalletConnect v2 client for dApp connections.
 */
@HiltAndroidApp
open class App : Application() {

    @Inject lateinit var mutationAuthorizationRefresher: MutationAuthorizationRefresher
    @Inject lateinit var soraCardLeftoverCleanup: SoraCardLeftoverCleanup
    @Inject lateinit var sharedExportCleanup: SharedExportCleanup
    @Inject lateinit var walletConnectStarter: WalletConnectStarter

    private val languagesHolder: LanguagesHolder = LanguagesHolder()
    @Suppress("unused")
    private val bebiEasterEgg = "B.E.B.I </3"

    override fun attachBaseContext(base: Context) {
        val contextManager = ContextManager.getInstanceOrInit(base, languagesHolder)
        super.attachBaseContext(contextManager.setLocale(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val contextManager = ContextManager.getInstanceOrInit(this, languagesHolder)
        contextManager.setLocale(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Set before any Activity exists. Set later, AppCompat restarts the first Activity to apply it, which
        // blanks the screen between the launch splash and the wallet on every start.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        // Sound and touch for transfers; nothing is loaded until a screen asks for it.
        TransferFeedback.install(this)

        initializeTonAddressCode()

        OptionsProvider.APPLICATION_ID = BuildConfig.LIBRARY_PACKAGE_NAME
        OptionsProvider.CURRENT_VERSION_CODE = BuildConfig.VERSION_CODE
        OptionsProvider.CURRENT_VERSION_NAME = BuildConfig.VERSION_NAME
        OptionsProvider.CURRENT_BUILD_TYPE = BuildConfig.BUILD_TYPE

        mutationAuthorizationRefresher.start()
        soraCardLeftoverCleanup.start()
        sharedExportCleanup.start()

        // WalletConnect v2 (in-repo client, BuildConfig.WALLET_CONNECT_PROJECT_ID). It deletes the removed
        // SDK's files once and connects to the relay only while a pairing or session is stored.
        initializeWalletConnectSafely(
            initialize = walletConnectStarter::start,
            onFailure = RuntimeException::printStackTrace
        )
    }
}
