package jp.co.soramitsu.wallet.impl.data.buyToken

import android.content.Context
import jp.co.soramitsu.common.utils.hmacSHA256
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.common.utils.toBase64
import jp.co.soramitsu.common.utils.toHexColor
import jp.co.soramitsu.common.utils.urlEncoded
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.model.BuyTokenRegistry

class MoonPayProvider(
    private val host: String,
    private val privateKey: String,
    private val publicKey: String
) : ExternalProvider {

    override val name: String = "Moonpay"

    override val icon: Int = R.drawable.ic_moonpay

    override fun createIntegrator(chainAsset: Asset, address: String): BuyTokenRegistry.Integrator<Context> {
        return MoonPayIntegrator(host, privateKey, publicKey, chainAsset, address)
    }

    class MoonPayIntegrator(
        private val host: String,
        private val privateKey: String,
        private val publicKey: String,
        private val tokenType: Asset,
        private val address: String
    ) : BuyTokenRegistry.Integrator<Context> {

        override fun integrate(using: Context) {
            using.showBrowser(createPurchaseLink(using))
        }

        private fun createPurchaseLink(context: Context): String {
            val color = context.getColor(R.color.colorAccent).toHexColor()

            val urlParams = buildString {
                append("?").append("apiKey").append("=").append(publicKey)
                append("&").append("currencyCode").append("=").append(tokenType.symbol)
                append("&").append("walletAddress").append("=").append(address)
                append("&").append("colorCode").append("=").append(color.urlEncoded())
                append("&").append("showWalletAddressForm").append("=").append(true)
                append("&").append("redirectURL").append("=").append(ExternalProvider.REDIRECT_URL_BASE.urlEncoded())
            }

            val signature = urlParams.hmacSHA256(privateKey).toBase64()

            return buildString {
                append("https://").append(host)
                append(urlParams)
                append("&").append("signature").append("=").append(signature.urlEncoded())
            }
        }
    }
}
