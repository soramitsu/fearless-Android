package jp.co.soramitsu.soracard.impl.presentation.getmorexor

import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.api.presentation.SoraCardRouter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GetMoreXorViewModel @Inject constructor(
    private val interactor: SoraCardInteractor,
    private val router: SoraCardRouter
) : BaseViewModel(), GetMoreXorScreenInterface {

    override fun onBackClicked() {
        router.back()
    }

    override fun onSwapForXorClick() {
        launch {
            interactor.xorAssetFlow().firstOrNull()?.let {
                router.back()
                router.openSwapTokensScreen(it.token.configuration.chainId, null, it.token.configuration.id)
            }
        }
    }

    override fun onBuyXorClick() {
        router.showBuyCrypto()
    }
}
