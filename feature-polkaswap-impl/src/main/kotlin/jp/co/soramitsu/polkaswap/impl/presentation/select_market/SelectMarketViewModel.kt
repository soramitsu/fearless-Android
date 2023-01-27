package jp.co.soramitsu.polkaswap.impl.presentation.select_market

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.map
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SelectMarketViewModel @Inject constructor(
    private val polkaswapRouter: PolkaswapRouter,
    private val polkaswapInteractor: PolkaswapInteractor
) : BaseViewModel() {

    val state = polkaswapInteractor.bestDexIdFlow.map {
        it.map { dex ->
            polkaswapInteractor.availableMarkets[dex] ?: listOf(Market.SMART)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoadingState.Loading())

    fun marketSelected(market: Market) {
        polkaswapRouter.backWithResult(SelectMarketFragment.MARKET_KEY to market)
    }
}
