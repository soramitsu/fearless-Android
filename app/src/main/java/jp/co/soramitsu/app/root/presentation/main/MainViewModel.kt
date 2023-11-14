package jp.co.soramitsu.app.root.presentation.main

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.DisclaimerAppearanceSource
import jp.co.soramitsu.polkaswap.api.models.DisclaimerVisibilityStatus
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val interactor: RootInteractor,
    private val walletRouter: WalletRouter,
    private val polkaswapRouter: PolkaswapRouter,
    private val polkaswapInteractor: PolkaswapInteractor,
    externalRequirements: MutableStateFlow<ChainConnection.ExternalRequirement>
) : BaseViewModel() {

    private var disclaimerVisibilityStatus =
        DisclaimerVisibilityStatus(
            DisclaimerAppearanceSource.BottomAppBarAction to false
        )

    init {
        externalRequirements.value = ChainConnection.ExternalRequirement.ALLOWED

        polkaswapInteractor.observeDisclaimerVisibilityStatus().onEach {
            disclaimerVisibilityStatus = it

            if (!it.visibility)
                return@onEach

            when(it.source) {
                DisclaimerAppearanceSource.BottomAppBarAction ->
                    walletRouter.openSwapTokensScreen(
                        chainId = null,
                        assetIdFrom = null,
                        assetIdTo = null
                    )
                else -> { /* DO NOTHING */ }
            }
        }.launchIn(viewModelScope)
    }

    val stakingAvailableLiveData = interactor.stakingAvailableFlow()
        .asLiveData()

    fun navigateToSwapScreen() {
        if (disclaimerVisibilityStatus.visibility) {
            walletRouter.openSwapTokensScreen(
                chainId = null,
                assetIdFrom = null,
                assetIdTo = null
            )
        } else {
            polkaswapRouter.openPolkaswapDisclaimer(DisclaimerAppearanceSource.BottomAppBarAction)
        }
    }
}
