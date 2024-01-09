package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletSelectorViewState
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WalletSelectorViewModel @Inject constructor(
    accountListingMixin: AccountListingMixin,
    private val router: WalletRouter,
    private val updatesMixin: UpdatesMixin,
    private val totalBalanceUseCase: TotalBalanceUseCase,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(), UpdatesProviderUi by updatesMixin {

    private val tag = savedStateHandle.get<String>(WalletSelectorFragment.TAG_ARGUMENT_KEY)!!
    private val selectedWalletId = savedStateHandle.get<Long?>(WalletSelectorFragment.SELECTED_WALLET_ID)
    private val walletSelectionMode = savedStateHandle[WalletSelectorFragment.WALLET_SELECTION_MODE] ?: WalletSelectionMode.CurrentWallet

    private val walletItemsFlow = accountListingMixin.accountsFlow(AddressIconGenerator.SIZE_BIG).mapList {
        val balanceModel = totalBalanceUseCase(it.id)

        WalletItemViewState(
            id = it.id,
            title = it.name,
            isSelected = it.isSelected,
            walletIcon = it.picture.value,
            balance = balanceModel.balance.formatFiat(balanceModel.fiatSymbol),
            changeBalanceViewState = ChangeBalanceViewState(
                percentChange = balanceModel.rateChange?.formatAsChange().orEmpty(),
                fiatChange = balanceModel.balanceChange.abs().formatFiat(balanceModel.fiatSymbol)
            )
        )
    }
        .inBackground()
        .share()
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
                    selectedWallet ?: walletItems.first { it.isSelected }
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
