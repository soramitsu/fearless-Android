package jp.co.soramitsu.app.root.presentation.main

import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.hasTon
import kotlinx.coroutines.flow.map

@HiltViewModel
class MainViewModel @Inject constructor(
    interactor: RootInteractor,
    accountInteractor: AccountInteractor,
    private val walletRouter: WalletRouter,
    private val polkaswapRouter: PolkaswapRouter,
    private val polkaswapInteractor: PolkaswapInteractor,
    externalRequirements: MutableStateFlow<ChainConnection.ExternalRequirement>
) : BaseViewModel() {

    init {
        externalRequirements.value = ChainConnection.ExternalRequirement.ALLOWED
    }

    val isTonAccountSelectedFlow = accountInteractor.selectedMetaAccountFlow().map {
        it.hasTon
    }

    fun navigateToSwapScreen() {
        walletRouter.openSwapTokensScreen(
            chainId = null,
            assetIdFrom = null,
            assetIdTo = null
        )

        if (!polkaswapInteractor.hasReadDisclaimer) {
            polkaswapRouter.openPolkaswapDisclaimerFromMainScreen()
        }
    }
}
