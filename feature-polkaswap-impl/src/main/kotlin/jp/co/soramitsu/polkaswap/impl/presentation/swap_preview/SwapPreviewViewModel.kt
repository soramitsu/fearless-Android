package jp.co.soramitsu.polkaswap.impl.presentation.swap_preview

import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import javax.inject.Inject

@HiltViewModel
class SwapPreviewViewModel @Inject constructor(
    private val polkaswapRouter: PolkaswapRouter
) : BaseViewModel(), SwapPreviewCallbacks {

    override fun onBackClick() {
        polkaswapRouter.back()
    }

    override fun onConfirmClick() {
        polkaswapRouter.openOperationSuccess(
            "0x113afabc81e58aab0a15d8e4caa9068c1af903f96de51b14729262a9d54c2472",
            chainId = soraMainChainId
        )
        // TODO: onConfirmClick
    }
}
