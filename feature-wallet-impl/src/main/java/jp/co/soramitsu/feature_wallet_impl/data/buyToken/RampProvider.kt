package jp.co.soramitsu.feature_wallet_impl.data.buyToken

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_api.domain.model.Token

private const val RAMP_APP_NAME = "Fearless Wallet"
private const val RAMP_APP_LOGO = "https://raw.githubusercontent.com/sora-xor/sora-branding/master/Fearless-Wallet-brand/favicon.png"

private const val RAMP_FINAL_URL = "https://fearlesswallet.io/buy/success"

class RampProvider(
    private val host: String,
    private val apiToken: String
) : WebViewProvider {

    override val supportedTypes = setOf(Token.Type.DOT)

    override fun createIntegrator(type: Token.Type, address: String): BuyTokenRegistry.Integrator<WebView> {
        if (!isTokenSupported(type)) {
            throw BuyTokenRegistry.Provider.UnsupportedTokenException()
        }

        return RampIntegrator(host, apiToken, type, address)
    }

    class RampIntegrator(
        private val host: String,
        private val apiToken: String,
        private val tokenType: Token.Type,
        private val address: String
    ) : BuyTokenRegistry.Integrator<WebView> {

        @SuppressLint("SetJavaScriptEnabled")
        override fun integrate(using: WebView, callback: BuyTokenRegistry.Integrator.Callback) {
            using.settings.javaScriptEnabled = true

            using.webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return if (request.url.toString() == RAMP_FINAL_URL) {
                        callback.buyCompleted()

                        true
                    } else {
                        false
                    }
                }
            }

            using.loadUrl(createPurchaseLink())
        }

        private fun createPurchaseLink(): String {

            return Uri.Builder()
                .scheme("https")
                .authority(host)
                .appendQueryParameter("swapAsset", tokenType.displayName)
                .appendQueryParameter("userAddress", address)
                .appendQueryParameter("hostApiKey", apiToken)
                .appendQueryParameter("hostAppName", RAMP_APP_NAME)
                .appendQueryParameter("hostLogoUrl", RAMP_APP_LOGO)
                .appendQueryParameter("finalUrl", RAMP_FINAL_URL)
                .build()
                .toString()
        }
    }
}