package jp.co.soramitsu.walletconnect.impl.presentation.chainschooser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.walletconnect.model.ChainChooseResult
import co.jp.soramitsu.walletconnect.model.ChainChooseState
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.walletconnect.impl.presentation.caip2id
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChainChooseViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    chainInteractor: ChainInteractor,
    savedStateHandle: SavedStateHandle,
    private val accountInteractor: AccountInteractor
) : BaseViewModel() {

    private val initialState: ChainChooseState? = savedStateHandle[ChainChooseFragment.KEY_STATE_ID]

    private val enteredChainQueryFlow = MutableStateFlow("")
    private val selectedChainIdsFlow = MutableStateFlow(initialState?.selected?.toSet().orEmpty())

    private val chainsFlow = combine(chainInteractor.getChainsFlow(), selectedChainIdsFlow) { chains, selectedChainIds ->
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
        needed.map { it.toChainItemState(isSelected = it.caip2id in selectedChainIds) }
    }.stateIn(this, SharingStarted.Eagerly, null)

    val state = combine(chainsFlow, enteredChainQueryFlow) { chainItems, searchQuery ->
        val chains = chainItems
            ?.filter {
                searchQuery.isEmpty() || it.title.contains(searchQuery, true)
            }
            ?.sortedBy { it.title }

        ChainSelectScreenViewState(
            chains = chains,
            searchQuery = searchQuery,
            isViewMode = initialState?.isViewMode == true
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChainSelectScreenViewState.default)

    fun onChainSelected(chainItemState: ChainItemState?) {
        val chainId = chainItemState?.caip2id
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
        val allChainsSelected = chainsFlow.value?.any { it.isSelected.not() } == false
        if (allChainsSelected) {
            selectedChainIdsFlow.value = emptySet()
        } else {
            selectedChainIdsFlow.value = chainsFlow.value.orEmpty().map { it.caip2id }.toSet()
        }
    }

    fun onDoneClicked() {
        launch {
            val selectedChainIds = state.value.chains?.filter { it.isSelected }?.map { it.caip2id }.orEmpty().toSet()

            val result = ChainChooseResult(selectedChainIds)

            walletRouter.backWithResult(
                ChainChooseFragment.RESULT to result
            )
        }
    }
}
