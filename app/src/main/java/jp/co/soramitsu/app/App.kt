package jp.co.soramitsu.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import dagger.hilt.android.HiltAndroidApp
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.OptionsProvider
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate

/**
 * Application entry point.
 *
 * - Boots Hilt DI via `@HiltAndroidApp`.
 * - Applies current locale using `ContextManager` and `LanguagesHolder`.
 * - Publishes build metadata (version, build type) through `OptionsProvider`.
 * - Initializes WalletConnect v2 (Reown SDK) for dApp connections.
 */
@HiltAndroidApp
open class App : Application() {

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

        OptionsProvider.APPLICATION_ID = BuildConfig.LIBRARY_PACKAGE_NAME
        OptionsProvider.CURRENT_VERSION_CODE = BuildConfig.VERSION_CODE
        OptionsProvider.CURRENT_VERSION_NAME = BuildConfig.VERSION_NAME
        OptionsProvider.CURRENT_BUILD_TYPE = BuildConfig.BUILD_TYPE

        // WalletConnect v2 setup (requires BuildConfig.WALLET_CONNECT_PROJECT_ID)
        setupWalletConnect()
    }

    /**
     * Configure the WalletConnect v2 client.
     *
     * Uses the Reown SDK with `AUTOMATIC` connection type and the project ID from
     * BuildConfig (`WALLET_CONNECT_PROJECT_ID`). When changing the relay or metadata
     * fields (name, description, icons), ensure they match Brand/App Store guidelines.
     */
    private fun setupWalletConnect() {
        initializeWalletConnectSafely(
            initialize = {
                val connectionType = ConnectionType.AUTOMATIC
                val projectId = BuildConfig.WALLET_CONNECT_PROJECT_ID
                val relayUrl = "relay.walletconnect.com"
                val serverUrl = "wss://$relayUrl?projectId=${projectId}"

                val appMetaData = Core.Model.AppMetaData(
                    name = "Fearless wallet",
                    description = "Defi wallet",
                    url = "https://fearlesswallet.io",
                    icons = listOf("https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/icons/FW%20icon%20128.png"),
                    redirect = "fearless-wallet-wc://request"
                )

                CoreClient.initialize(
                    relayServerUrl = serverUrl,
                    connectionType = connectionType,
                    application = this,
                    metaData = appMetaData,
                    onError = {
                        it.throwable.printStackTrace()
                    }
                )

                val initParams = Wallet.Params.Init(core = CoreClient)

                WalletKit.initialize(
                    params = initParams,
                    onSuccess = {
                        WCDelegate.registerDelegatesIfReady()
                            .onSuccess { WCDelegate.refreshConnections() }
                    },
                    onError = { error ->
                        // Will log exceptions if initialization fails (e.g., invalid project ID, network issues)
                        error.throwable.printStackTrace()
                    }
                )
            },
            onFailure = RuntimeException::printStackTrace
        )
    }
}
