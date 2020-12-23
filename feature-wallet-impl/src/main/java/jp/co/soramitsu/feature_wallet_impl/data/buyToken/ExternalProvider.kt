package jp.co.soramitsu.feature_wallet_impl.data.buyToken

import android.content.Context
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry

interface ExternalProvider : BuyTokenRegistry.Provider<BuyTokenRegistry.Integrator<Context>> {
    val redirectLink: String
}