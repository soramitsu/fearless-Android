package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletSelectorViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SelectWalletViewModel @Inject constructor(
    accountListingMixin: AccountListingMixin,
    private val accountInteractor: AccountInteractor,
    private val nomisScoreInteractor: NomisScoreInteractor,
    private val router: WalletRouter,
    private val getTotalBalance: TotalBalanceUseCase,
    private val resourceManager: ResourceManager,
    private val pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario
) : BaseViewModel() {

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
        getTotalBalance.observe(metaId).onEach { balanceModel ->
            walletItemsFlow.update {
                it.map {  state ->
                    if(state.id == metaId) {
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

    private val selectedWalletItem = MutableStateFlow<WalletItemViewState?>(null)
    val googleAuthorizeLiveData = MutableLiveData<Event<Unit>>()
    val importPreInstalledWalletLiveData = MutableLiveData<Event<Unit>>()

    val state = combine(
        walletItemsFlow,
        selectedWalletItem
    ) { walletItems, selectedWallet ->
        WalletSelectorViewState(
            wallets = walletItems,
            selectedWallet = selectedWallet ?: walletItems.firstOrNull { it.isSelected }
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

    fun onBackClicked() {
        router.back()
    }

    fun onWalletOptionsClick(item: WalletItemViewState) {
        router.openOptionsWallet(item.id)
    }

    fun authorizeGoogle(launcher: ActivityResultLauncher<Intent>) {
        launch {
            if (accountInteractor.authorizeGoogleBackup(launcher)) {
                openAddWalletThroughGoogleScreen()
            }
        }
    }

    fun openAddWalletThroughGoogleScreen() {
        launch {
            runCatching {
                accountInteractor.getGoogleBackupAccounts()
            }.onFailure {
                showError(
                    title = resourceManager.getString(R.string.common_error_general_title),
                    message = resourceManager.getString(R.string.no_access_to_google),
                    positiveClick = router::back
                )
            }.onSuccess { backupAccounts ->
                router.openImportRemoteWalletDialog()
            }
        }
    }

    fun onGoogleLoginError(message: String?) {
        showError("GoogleLoginError: ${message.orEmpty()}")
    }

    fun onQrScanResult(result: String?) {
        if (result == null) {
            showError("Can't scan qr code")
            return
        }

        viewModelScope.launch {
            pendulumPreInstalledAccountsScenario.import(result)
                .onFailure {
                    showError(it)
                }
                .onSuccess {
                    router.back()
                }
        }
    }

    fun onScoreClick(state: WalletItemViewState) {
        router.openScoreDetailsScreen(state.id)
    }
}
