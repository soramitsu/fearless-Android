package jp.co.soramitsu.feature_wallet_impl.data.buyToken

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

private const val RAMP_APP_NAME = "Fearless Wallet"
private const val RAMP_APP_LOGO = "https://raw.githubusercontent.com/sora-xor/sora-branding/master/Fearless-Wallet-brand/fearless-wallet-logo-ramp.png"

class RampProvider(
    private val host: String,
    private val apiToken: String
) : ExternalProvider {

    override val supportedTokens = mapOf(
        "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3" to listOf("DOT"),
        "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe" to listOf("KSM")
    )

    override val name: String = "Ramp"

    override val icon: Int = R.drawable.ic_ramp

    override fun createIntegrator(chainAsset: Chain.Asset, address: String): BuyTokenRegistry.Integrator<Context> {
        if (!isTokenSupported(chainAsset)) {
            throw BuyTokenRegistry.Provider.UnsupportedTokenException()
        }

        return RampIntegrator(host, apiToken, chainAsset, address)
    }

    class RampIntegrator(
        private val host: String,
        private val apiToken: String,
        private val chainAsset: Chain.Asset,
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
                .appendQueryParameter("swapAsset", chainAsset.symbol)
                .appendQueryParameter("userAddress", address)
                .appendQueryParameter("hostApiKey", apiToken)
                .appendQueryParameter("hostAppName", RAMP_APP_NAME)
                .appendQueryParameter("hostLogoUrl", RAMP_APP_LOGO)
                .appendQueryParameter("finalUrl", ExternalProvider.REDIRECT_URL_BASE)
                .build()
                .toString()
        }
    }
}
