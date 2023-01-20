package jp.co.soramitsu.polkaswap.impl.presentation.select_market

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter

@HiltViewModel
class SelectMarketViewModel @Inject constructor(
    private val polkaswapRouter: PolkaswapRouter,
) : BaseViewModel() {

    fun marketSelected(market: Market) {
        polkaswapRouter.backWithResult(SelectMarketFragment.MARKET_KEY to market)
    }
}
