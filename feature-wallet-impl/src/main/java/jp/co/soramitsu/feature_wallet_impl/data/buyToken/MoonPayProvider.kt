package jp.co.soramitsu.feature_wallet_impl.data.buyToken

import android.content.Context
import jp.co.soramitsu.common.utils.hmacSHA256
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.common.utils.toBase64
import jp.co.soramitsu.common.utils.toHexColor
import jp.co.soramitsu.common.utils.urlEncoded
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.R

class MoonPayProvider(
    private val host: String,
    private val privateKey: String,
    private val publicKey: String,
) : ExternalProvider {

    override val supportedTokens: Set<Token.Type> = setOf(Token.Type.DOT)

    override val name: String = "Moonpay"

    override val icon: Int = R.drawable.ic_moonpay

    override fun createIntegrator(tokenType: Token.Type, address: String): BuyTokenRegistry.Integrator<Context> {
        return MoonPayIntegrator(host, privateKey, publicKey, tokenType, address)
    }

    class MoonPayIntegrator(
        private val host: String,
        private val privateKey: String,
        private val publicKey: String,
        private val tokenType: Token.Type,
        private val address: String,
    ) : BuyTokenRegistry.Integrator<Context> {

        override fun integrate(using: Context) {
            using.showBrowser(createPurchaseLink(using))
        }

        private fun createPurchaseLink(context: Context): String {
            val color = context.getColor(R.color.colorAccent).toHexColor()

            val urlParams = buildString {
                append("?").append("apiKey").append("=").append(publicKey)
                append("&").append("currencyCode").append("=").append(tokenType.displayName)
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
