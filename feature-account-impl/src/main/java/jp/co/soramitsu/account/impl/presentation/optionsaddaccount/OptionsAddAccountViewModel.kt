package jp.co.soramitsu.account.impl.presentation.optionsaddaccount

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AssetNotNeedAccountUseCase
import jp.co.soramitsu.account.api.presentation.actions.AddAccountPayload
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.optionsaddaccount.OptionsAddAccountFragment.Companion.KEY_PAYLOAD
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OptionsAddAccountViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val accountInteractor: AccountInteractor,
    private val assetNotNeedAccount: AssetNotNeedAccountUseCase,
    private val accountRouter: AccountRouter
) : BaseViewModel() {

    private val selectedWallet = accountInteractor.selectedMetaAccountFlow()
        .inBackground()
        .share()

    val state: StateFlow<OptionsAddAccountScreenViewState> = selectedWallet.mapNotNull {
        savedStateHandle.get<AddAccountPayload>(KEY_PAYLOAD)?.let { payload ->
            OptionsAddAccountScreenViewState(
                metaId = it.id,
                chainId = payload.chainId,
                chainName = payload.chainName,
                markedAsNotNeed = payload.markedAsNotNeed,
                assetId = payload.assetId
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        OptionsAddAccountScreenViewState(
            metaId = 1,
            chainId = "",
            chainName = "Dotsama",
            markedAsNotNeed = false,
            assetId = ""
        )
    )

    fun createAccount(chainId: ChainId, metaId: Long) {
        accountRouter.openOnboardingNavGraph(chainId = chainId, metaId = metaId, isImport = false)
    }

    fun importAccount(chainId: ChainId, metaId: Long) {
        accountRouter.openOnboardingNavGraph(chainId = chainId, metaId = metaId, isImport = true)
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
