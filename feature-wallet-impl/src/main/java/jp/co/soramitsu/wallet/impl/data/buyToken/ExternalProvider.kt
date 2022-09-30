package jp.co.soramitsu.wallet.impl.data.buyToken

import android.content.Context
import jp.co.soramitsu.wallet.impl.domain.model.BuyTokenRegistry

interface ExternalProvider : BuyTokenRegistry.Provider<BuyTokenRegistry.Integrator<Context>> {

    companion object {
        const val REDIRECT_URL_BASE = "fearless://buy-success"
    }
}
