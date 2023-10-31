package jp.co.soramitsu.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import com.walletconnect.android.relay.ConnectionType
import com.walletconnect.android.relay.NetworkClientTimeout
import com.walletconnect.web3.wallet.client.Wallet
import com.walletconnect.web3.wallet.client.Web3Wallet
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import jp.co.soramitsu.common.data.network.OptionsProvider
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

    override fun onCreate() {
        super.onCreate()

        OptionsProvider.APPLICATION_ID = BuildConfig.APPLICATION_ID
        OptionsProvider.CURRENT_VERSION_CODE = BuildConfig.VERSION_CODE
        OptionsProvider.CURRENT_VERSION_NAME = BuildConfig.VERSION_NAME
        OptionsProvider.CURRENT_BUILD_TYPE = BuildConfig.BUILD_TYPE

//        setupWalletConnect()
    }

    fun setupWalletConnect() {
        Log.d("&&&", "CoreClient start WC initialization")
        val connectionType = ConnectionType.AUTOMATIC // ConnectionType.AUTOMATIC or ConnectionType.MANUAL
        val projectId = jp.co.soramitsu.common.BuildConfig.WALLET_CONNECT_PROJECT_ID // Project ID at https://cloud.walletconnect.com/
        val relayUrl = "relay.walletconnect.com"
        val serverUrl = "wss://$relayUrl?projectId=${projectId}"

        val appMetaData = Core.Model.AppMetaData(
            name = "Fearless wallet",
            description = "Defi wallet",
            url = "https://fearlesswallet.io",
            icons = listOf(""),
            redirect = "fearless-wallet-wc://request"
        )

        CoreClient.initialize(
            relayServerUrl = serverUrl,
            connectionType = connectionType,
            application = this,
            metaData = appMetaData,
            networkClientTimeout = NetworkClientTimeout(60, TimeUnit.SECONDS),
            onError = {
                println("!!! error CoreClient.initialize = ${it.throwable.message}")
                it.throwable.printStackTrace()
            }
        )

        val initParams = Wallet.Params.Init(core = CoreClient)

        Web3Wallet.initialize(initParams, onSuccess = {
            println("!!! Web3Wallet.initialize = onSuccess")
        }) { error ->
            // Error will be thrown if there's an issue during initialization
            println("!!! error Web3Wallet.initialize = ${error.throwable.message}")
            error.throwable.printStackTrace()
        }

        Log.d("&&&", "CoreClient finish WC initialization")
    }
}
