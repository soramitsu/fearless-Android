package jp.co.soramitsu.feature_wallet_impl.data.buyToken

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_api.domain.model.Token

private const val RAMP_APP_NAME = "Fearless Wallet"
private const val RAMP_APP_LOGO = "https://raw.githubusercontent.com/sora-xor/sora-branding/master/Fearless-Wallet-brand/fearless-wallet-logo-ramp.png"

private const val RAMP_FINAL_URL = "fearless://buy-success"

class RampProvider(
    private val host: String,
    private val apiToken: String
) : ExternalProvider {
    override val redirectLink = RAMP_FINAL_URL

    override val supportedTokens = setOf(Token.Type.DOT, Token.Type.KSM)

    override fun createIntegrator(type: Token.Type, address: String): BuyTokenRegistry.Integrator<Context> {
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
    ) : BuyTokenRegistry.Integrator<Context> {

        @SuppressLint("SetJavaScriptEnabled")
        override fun integrate(using: Context) {
            using.showBrowser(createPurchaseLink())
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