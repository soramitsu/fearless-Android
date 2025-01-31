package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletSelectorViewState
import jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class WalletSelectorViewModel @Inject constructor(
    accountListingMixin: AccountListingMixin,
    private val router: WalletRouter,
    private val totalBalanceUseCase: TotalBalanceUseCase,
    private val nomisScoreInteractor: NomisScoreInteractor,
    savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    private val tag = savedStateHandle.get<String>(WalletSelectorFragment.TAG_ARGUMENT_KEY)!!
    private val selectedWalletId =
        savedStateHandle.get<Long?>(WalletSelectorFragment.SELECTED_WALLET_ID)
    private val walletSelectionMode = savedStateHandle[WalletSelectorFragment.WALLET_SELECTION_MODE]
        ?: WalletSelectionMode.CurrentWallet

    private val walletItemsFlow = MutableStateFlow<List<WalletItemViewState>>(emptyList())

    init {
        accountListingMixin.accountsFlow(AddressIconGenerator.SIZE_BIG)
            .distinctUntilChanged()
            .inBackground()
            .onEach { newList ->
                walletItemsFlow.update {
                    newList.map {
                        WalletItemViewState(
                            id = it.id,
                            title = it.name,
                            isSelected = it.isSelected,
                            walletIcon = it.picture.value,
                            balance = null,
                            changeBalanceViewState = null
                        )
                    }
                }
            }
            .onEach { accounts ->
                accounts.forEach { observeTotalBalance(it.id) }
                observeScores()
            }
            .launchIn(viewModelScope)
    }

    private fun observeTotalBalance(metaId: Long) {
        totalBalanceUseCase.observe(metaId).onEach { balanceModel ->
            walletItemsFlow.update {
                it.map { state ->
                    if (state.id == metaId) {
                        state.copy(
                            balance = balanceModel.balance.formatFiat(balanceModel.fiatSymbol),
                            changeBalanceViewState = ChangeBalanceViewState(
                                percentChange = balanceModel.rateChange?.formatAsChange().orEmpty(),
                                fiatChange = balanceModel.balanceChange.abs()
                                    .formatFiat(balanceModel.fiatSymbol)
                            )
                        )
                    } else {
                        state
                    }
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun observeScores() {
        nomisScoreInteractor.observeNomisScores()
            .onEach { scores ->
                walletItemsFlow.update { oldStates ->
                    oldStates.map { state ->
                        val score = scores.find { it.metaId == state.id }
                        score?.let { state.copy(score = score.score) } ?: state
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private val selectedWalletItem = walletItemsFlow
        .map { it.firstOrNull { it.id == selectedWalletId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state = combine(
        walletItemsFlow,
        selectedWalletItem
    ) { walletItems, selectedWallet ->
        WalletSelectorViewState(
            wallets = walletItems,
            selectedWallet = when (walletSelectionMode) {
                WalletSelectionMode.CurrentWallet -> {
                    selectedWallet ?: walletItems.firstOrNull { it.isSelected }
                }

                WalletSelectionMode.ExternalSelectedWallet -> {
                    selectedWallet ?: selectedWalletId?.let {
                        walletItems.firstOrNull { it.id == selectedWalletId }
                    }
                }
            }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        WalletSelectorViewState(
            emptyList(),
            null
        )
    )

    fun onWalletSelected(item: WalletItemViewState) {
        viewModelScope.launch {
            router.setWalletSelectorPayload(WalletSelectorPayload(tag, item.id))
            router.backWithResult(WalletSelectorFragment.RESULT_ADDRESS to item.id)
        }
    }

    fun onBackClicked() {
        router.back()
    }
}
