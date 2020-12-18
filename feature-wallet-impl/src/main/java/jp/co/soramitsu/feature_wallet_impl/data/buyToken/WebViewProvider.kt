package jp.co.soramitsu.feature_wallet_impl.data.buyToken

import android.webkit.WebView
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry

interface WebViewProvider : BuyTokenRegistry.Provider<BuyTokenRegistry.Integrator<WebView>>