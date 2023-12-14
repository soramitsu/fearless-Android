package jp.co.soramitsu.walletconnect.impl.presentation.chainschooser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.walletconnect.model.ChainChooseResult
import co.jp.soramitsu.walletconnect.model.ChainChooseState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.walletconnect.impl.presentation.caip2id
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ChainChooseViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    private val walletInteractor: WalletInteractor,
    chainInteractor: ChainInteractor,
    savedStateHandle: SavedStateHandle,
    private val accountInteractor: AccountInteractor
) : BaseViewModel() {

    private val initialState: ChainChooseState? = savedStateHandle[ChainChooseFragment.KEY_STATE_ID]

    private val chainsFlow = chainInteractor.getChainsFlow().map { chains ->
        val meta = accountInteractor.selectedMetaAccount()
        val ethBasedChainAccounts = meta.chainAccounts.filter { it.value.chain?.isEthereumBased == true }
        val ethBasedChains = chains.filter { it.isEthereumBased }
        val filtered = if (meta.ethereumPublicKey == null && ethBasedChains.size != ethBasedChainAccounts.size) {
            val ethChainsWithNoAccounts = ethBasedChains.filter { it.id !in ethBasedChainAccounts.keys }
            chains.filter { it !in ethChainsWithNoAccounts }
        } else {
            chains
        }
        val needed = filtered.filter {
            it.caip2id in initialState?.items.orEmpty()
        }
        needed.map { it.toChainItemState() }
    }.stateIn(this, SharingStarted.Eagerly, null)

    private val enteredChainQueryFlow = MutableStateFlow("")
    private val selectedChainIdsFlow = MutableStateFlow<Set<String>>(initialState?.selected?.toSet().orEmpty())

    val state = combine(chainsFlow, enteredChainQueryFlow, selectedChainIdsFlow) { chainItems, searchQuery, selectedChainIds ->
        val chains = chainItems
            ?.filter {
                searchQuery.isEmpty() || it.title.contains(searchQuery, true)
            }
            ?.sortedBy { it.title }
            ?.map {
                it.copy(isSelected = it.id in selectedChainIds)
            }

        ChainSelectScreenViewState(
            items = initialState?.items,
            chains = chains,
            searchQuery = searchQuery,
            isViewMode = initialState?.isViewMode == true
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChainSelectScreenViewState.default)

    fun onChainSelected(chainItemState: ChainItemState?) {
        val chainId = chainItemState?.id
        val currentSelectedIds = selectedChainIdsFlow.value.toMutableSet()
        chainId?.let {
            val isAdded = currentSelectedIds.add(it)
            if (!isAdded) {
                currentSelectedIds.remove(it)
            }
            selectedChainIdsFlow.value = currentSelectedIds
        }
    }

    fun onSearchInput(input: String) {
        enteredChainQueryFlow.value = input
    }

    fun onSelectAllClicked() {
        val chains = chainsFlow.value.orEmpty()
        selectedChainIdsFlow.value = chains.map { it.id }.toSet()
    }

    fun onDoneClicked() {
        launch {
            val selectedChainIds = state.value.chains?.filter { it.isSelected }?.map { it.id }.orEmpty().toSet()

            val result = ChainChooseResult(selectedChainIds)

            walletRouter.backWithResult(
                ChainChooseFragment.RESULT to result
            )
        }
    }

    fun onDialogClose() {
    }
}
