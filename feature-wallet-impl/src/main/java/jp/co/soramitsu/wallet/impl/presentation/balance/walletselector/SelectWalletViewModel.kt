package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletSelectorViewState
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SUBSTRATE_BLOCKCHAIN_TYPE = 0

@HiltViewModel
class SelectWalletViewModel @Inject constructor(
    accountListingMixin: AccountListingMixin,
    private val accountInteractor: AccountInteractor,
    private val router: WalletRouter,
    private val updatesMixin: UpdatesMixin,
    private val getTotalBalance: TotalBalanceUseCase
) : BaseViewModel(), UpdatesProviderUi by updatesMixin {

    private val accountsFlow = accountListingMixin.accountsFlow(AddressIconGenerator.SIZE_BIG)

    private val walletItemsFlow = accountsFlow.mapList {
        val balanceModel = getTotalBalance(it.id)

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
            accountInteractor.selectMetaAccount(item.id)
            selectedWalletItem.value = item
            router.back()
        }
    }

    fun addNewWallet() {
        router.openCreateAccountFromWallet()
    }

    fun importWallet() {
        router.openImportAccountScreenFromWallet(SUBSTRATE_BLOCKCHAIN_TYPE)
    }

    fun onBackClicked() {
        router.back()
    }

    fun onWalletOptionsClick(item: WalletItemViewState) {
        router.openOptionsWallet(item.id)
    }
}
