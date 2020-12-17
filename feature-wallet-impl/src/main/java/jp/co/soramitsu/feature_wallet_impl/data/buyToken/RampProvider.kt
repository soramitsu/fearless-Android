package jp.co.soramitsu.feature_wallet_impl.data.buyToken

import android.net.Uri
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_api.domain.model.Token

private const val RAMP_APP_NAME = "Fearless Wallet"
private const val RAMP_APP_LOGO = "https://raw.githubusercontent.com/sora-xor/sora-branding/master/Fearless-Wallet-brand/favicon.png"

class RampProvider(
    private val host: String,
    private val apiToken: String
) : BuyTokenRegistry.Provider {

    override val supportedTypes = setOf(Token.Type.DOT)

    override fun createPurchaseLink(type: Token.Type, address: String): String {
        if (!isTokenSupported(type)) {
            throw BuyTokenRegistry.Provider.UnsupportedTokenException()
        }

        return Uri.Builder()
            .scheme("https")
            .authority(host)
            .appendQueryParameter("swapAsset", type.displayName)
            .appendQueryParameter("userAddress", address)
            .appendQueryParameter("hostApiKey", apiToken)
            .appendQueryParameter("hostAppName", RAMP_APP_NAME)
            .appendQueryParameter("hostLogoUrl", RAMP_APP_LOGO)
            .build()
            .toString()
    }
}