package jp.co.soramitsu.wallet.impl.data.buyToken

import android.content.Context
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.common.utils.toHexColor
import jp.co.soramitsu.common.utils.urlEncoded
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.model.BuyTokenRegistry

class MoonPayProvider(
    private val host: String,
    private val publicKey: String
) : ExternalProvider {

    override val name: String = "Moonpay"

    override val icon: Int = R.drawable.ic_moonpay

    override fun createIntegrator(chainAsset: Asset, address: String): BuyTokenRegistry.Integrator<Context> {
        return MoonPayIntegrator(host, publicKey, chainAsset)
    }

    class MoonPayIntegrator(
        private val host: String,
        private val publicKey: String,
        private val tokenType: Asset
    ) : BuyTokenRegistry.Integrator<Context> {

        override fun integrate(using: Context) {
            using.showBrowser(createPurchaseLink(using))
        }

        private fun createPurchaseLink(context: Context): String {
            val color = context.getColor(R.color.colorAccentDark).toHexColor()

            return createMoonPayPurchaseLink(
                host = host,
                publicKey = publicKey,
                currencyCode = tokenType.symbol,
                color = color,
                redirectUrl = ExternalProvider.REDIRECT_URL_BASE
            )
        }
    }
}

private val MOONPAY_ALLOWED_HOSTS = setOf("buy-sandbox.moonpay.com", "buy.moonpay.com")
private val MOONPAY_COLOR = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")

/**
 * Builds the unsigned hosted-widget URL. Supplying walletAddress requires a
 * backend-generated signature, so the client intentionally leaves it for the
 * user to enter in MoonPay instead of embedding a server secret in the APK.
 */
internal fun createMoonPayPurchaseLink(
    host: String,
    publicKey: String,
    currencyCode: String,
    color: String,
    redirectUrl: String
): String {
    require(host in MOONPAY_ALLOWED_HOSTS) { "Unsupported MoonPay host" }
    require(publicKey.isNotBlank() && publicKey.length <= 256 && !publicKey.hasControlCharacters()) {
        "Invalid MoonPay public key"
    }
    require(currencyCode.isNotBlank() && currencyCode.length <= 64 && !currencyCode.hasControlCharacters()) {
        "Invalid MoonPay currency code"
    }
    require(MOONPAY_COLOR.matches(color)) { "Invalid MoonPay color" }
    require(
        redirectUrl.startsWith("https://") &&
            redirectUrl.length <= 2048 &&
            !redirectUrl.hasControlCharacters()
    ) { "Invalid MoonPay redirect URL" }

    val parameters = listOf(
        "apiKey" to publicKey,
        "currencyCode" to currencyCode,
        "colorCode" to color,
        "showWalletAddressForm" to "true",
        "redirectURL" to redirectUrl
    )

    return "https://$host/?" + parameters.joinToString("&") { (name, value) ->
        "$name=${value.urlEncoded()}"
    }
}

private fun String.hasControlCharacters(): Boolean = any { it.isISOControl() }
