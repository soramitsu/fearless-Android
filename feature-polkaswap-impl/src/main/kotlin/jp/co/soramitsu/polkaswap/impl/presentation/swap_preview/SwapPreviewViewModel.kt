package jp.co.soramitsu.polkaswap.impl.presentation.swap_preview

import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import javax.inject.Inject

@HiltViewModel
class SwapPreviewViewModel @Inject constructor(
    private val polkaswapRouter: PolkaswapRouter
) : BaseViewModel(), SwapPreviewCallbacks {

    override fun onBackClick() {
        polkaswapRouter.back()
    }

    override fun onConfirmClick() {
        // TODO: onConfirmClick
    }
}
