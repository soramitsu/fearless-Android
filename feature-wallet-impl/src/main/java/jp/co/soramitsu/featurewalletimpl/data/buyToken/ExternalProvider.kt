package jp.co.soramitsu.featurewalletimpl.data.buyToken

import android.content.Context
import jp.co.soramitsu.featurewalletapi.domain.model.BuyTokenRegistry

interface ExternalProvider : BuyTokenRegistry.Provider<BuyTokenRegistry.Integrator<Context>> {

    companion object {
        const val REDIRECT_URL_BASE = "fearless://buy-success"
    }
}
