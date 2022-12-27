package jp.co.soramitsu.polkaswap.impl.presentation.select_market

import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import javax.inject.Inject

@HiltViewModel
class SelectMarketViewModel @Inject constructor(
    private val polkaswapRouter: PolkaswapRouter
) : BaseViewModel()
