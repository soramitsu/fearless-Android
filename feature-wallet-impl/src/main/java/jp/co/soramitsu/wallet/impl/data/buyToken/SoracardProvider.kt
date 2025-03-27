package jp.co.soramitsu.wallet.impl.data.buyToken

import android.content.Context
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.model.BuyTokenRegistry

class SoracardProvider : ExternalProvider {

    override val name: String = "SORAcard"
    override val icon: Int = R.drawable.ic_soracard

    override fun createIntegrator(
        chainAsset: Asset,
        address: String
    ): BuyTokenRegistry.Integrator<Context> {
        return SoracardIntegrator()
    }

    class SoracardIntegrator : BuyTokenRegistry.Integrator<Context> {

        override fun integrate(using: Context) {
        }
    }
}
