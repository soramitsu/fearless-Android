package jp.co.soramitsu.account.impl.presentation.options_switch_node

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AssetNotNeedAccountUseCase
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OptionsSwitchNodeViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val accountInteractor: AccountInteractor,
    private val assetNotNeedAccount: AssetNotNeedAccountUseCase,
    private val accountRouter: AccountRouter
) : BaseViewModel() {

    private val selectedWallet = accountInteractor.selectedMetaAccountFlow()
        .inBackground()
        .share()

    val state: StateFlow<OptionsSwitchNodeScreenViewState> = selectedWallet.mapNotNull {
        val metaId = savedStateHandle.get<Long>(OptionsSwitchNodeFragment.KEY_META_ID)!!
        val chainId = savedStateHandle.get<ChainId>(OptionsSwitchNodeFragment.KEY_CHAIN_ID)!!
        val chainName = savedStateHandle.get<String>(OptionsSwitchNodeFragment.KEY_CHAIN_NAME)!!
        OptionsSwitchNodeScreenViewState(
            metaId = metaId,
            chainId = chainId,
            chainName = chainName
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        OptionsSwitchNodeScreenViewState(
            metaId = 1,
            chainId = "",
            chainName = "Dotsama"
        )
    )

    fun onSwitch(chainId: ChainId) {
        accountRouter.openNodes(chainId)
    }

    fun dontShowAgainClicked(chainId: ChainId, metaId: Long) {
        launch {
            assetNotNeedAccount.markChainAssetsNotNeed(chainId = chainId, metaId = metaId)
            accountRouter.back()
        }
    }

    fun onBackClicked() {
        accountRouter.back()
    }
}
