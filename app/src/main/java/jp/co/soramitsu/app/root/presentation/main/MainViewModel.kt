package jp.co.soramitsu.app.root.presentation.main

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@HiltViewModel
class MainViewModel @Inject constructor(
    private val interactor: RootInteractor,
    private val walletRouter: WalletRouter,
    private val polkaswapRouter: PolkaswapRouter,
    private val polkaswapInteractor: PolkaswapInteractor,
    externalRequirements: MutableStateFlow<ChainConnection.ExternalRequirement>
) : BaseViewModel() {

    init {
        externalRequirements.value = ChainConnection.ExternalRequirement.ALLOWED
        walletRouter.listenPolkaswapDisclaimerResultFlowFromMainScreen()
            .onEach {
                if (it) {
                    walletRouter.openSwapTokensScreen(
                        chainId = null,
                        assetIdFrom = null,
                        assetIdTo = null
                    )
                }
            }.launchIn(viewModelScope)
    }

    val stakingAvailableLiveData = interactor.stakingAvailableFlow()
        .asLiveData()

    fun navigateToSwapScreen() {
        val xorPswap = Pair("b774c386-5cce-454a-a845-1ec0381538ec", "37a999a2-5e90-4448-8b0e-98d06ac8f9d4")
//        polkaswapRouter.openAddLiquidity(xorPswap)
        if (polkaswapInteractor.hasReadDisclaimer) {
            walletRouter.openSwapTokensScreen(
                chainId = null,
                assetIdFrom = null,
                assetIdTo = null
            )
        } else {
            polkaswapRouter.openPolkaswapDisclaimerFromMainScreen()
        }
    }
}
