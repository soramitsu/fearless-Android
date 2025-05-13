package jp.co.soramitsu.wallet.impl.data.buyToken

import android.content.Context
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.model.BuyTokenRegistry

class CoinbaseProvider(private val host: String, private val appId: String): ExternalProvider {

    override val name: String = "Coinbase"
    override val icon: Int = R.drawable.ic_coinbase

    override fun createIntegrator(
        chainAsset: Asset,
        address: String
    ): BuyTokenRegistry.Integrator<Context> {
        return CoinbaseIntegrator(host, appId, chainAsset, address)
    }

    class CoinbaseIntegrator(
        private val host: String,
        private val appId: String,
        private val tokenType: Asset,
        private val address: String
    ) : BuyTokenRegistry.Integrator<Context> {

        override fun integrate(using: Context) {
            val link = createPurchaseLink()
            using.showBrowser(link)
        }

        private fun createPurchaseLink(): String {
            val params = tokenType.coinbaseUrl?.replace("{address}", address).orEmpty()
            val urlParams = buildString {
                append("?").append("appId").append("=").append(appId)
                .append("&").append(params)
            }

            return buildString {
                append("https://").append(host)
                append(urlParams)
            }
        }
    }
}
