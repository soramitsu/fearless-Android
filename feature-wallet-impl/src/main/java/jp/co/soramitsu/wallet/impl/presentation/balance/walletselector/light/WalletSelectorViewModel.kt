package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.GetTotalBalanceUseCase
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
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WalletSelectorViewModel @Inject constructor(
    accountListingMixin: AccountListingMixin,
    private val router: WalletRouter,
    private val updatesMixin: UpdatesMixin,
    private val getTotalBalanceUseCase: GetTotalBalanceUseCase,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(), UpdatesProviderUi by updatesMixin {

    private val tag = savedStateHandle.get<String>(WalletSelectorFragment.TAG_ARGUMENT_KEY)!!

    private val walletItemsFlow = accountListingMixin.accountsFlow(AddressIconGenerator.SIZE_BIG).mapList {
        val balanceModel = getTotalBalanceUseCase.invoke(it.id).first()

        WalletItemViewState(
            id = it.id,
            title = it.name,
            isSelected = it.isSelected,
            walletIcon = it.picture.value,
            balance = balanceModel.balance.formatAsCurrency(balanceModel.fiatSymbol),
            changeBalanceViewState = ChangeBalanceViewState(
                percentChange = balanceModel.rateChange?.formatAsChange().orEmpty(),
                fiatChange = balanceModel.balanceChange.abs().formatAsCurrency(balanceModel.fiatSymbol)
            )
        )
    }
        .inBackground()
        .share()
    private val selectedWalletItem = MutableStateFlow<WalletItemViewState?>(null)

    val state = combine(
        walletItemsFlow,
        selectedWalletItem
    ) { walletItems, selectedWallet ->
        WalletSelectorViewState(
            wallets = walletItems,
            selectedWallet = selectedWallet ?: walletItems.first { it.isSelected }
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
            selectedWalletItem.value = item
            router.setWalletSelectorPayload(WalletSelectorPayload(tag, item.id))
            router.back()
        }
    }

    fun onBackClicked() {
        router.back()
    }
}
