package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletSelectorViewState
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SUBSTRATE_BLOCKCHAIN_TYPE = 0

@HiltViewModel
class SelectWalletViewModel @Inject constructor(
    accountListingMixin: AccountListingMixin,
    private val accountInteractor: AccountInteractor,
    private val router: WalletRouter,
    private val updatesMixin: UpdatesMixin,
    private val getTotalBalance: TotalBalanceUseCase,
    private val backupService: BackupService,
    private val resourceManager: ResourceManager,
    private val pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario
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
    val googleAuthorizeLiveData = MutableLiveData<Event<Unit>>()
    val importPreInstalledWalletLiveData = MutableLiveData<Event<Unit>>()

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
        router.openSelectImportModeForResult()
            .onEach(::handleSelectedImportMode)
            .launchIn(viewModelScope)
    }

    fun onBackClicked() {
        router.back()
    }

    fun onWalletOptionsClick(item: WalletItemViewState) {
        router.openOptionsWallet(item.id)
    }

    private fun handleSelectedImportMode(importMode: ImportMode) {
        when (importMode) {
            ImportMode.Google -> {
                googleAuthorizeLiveData.value = Event(Unit)
            }
            ImportMode.Preinstalled -> {
                importPreInstalledWalletLiveData.value = Event(Unit)
            }
            else -> {
                router.openImportAccountScreen(
                    blockChainType = SUBSTRATE_BLOCKCHAIN_TYPE,
                    importMode = importMode
                )
            }
        }
    }

    fun authorizeGoogle(launcher: ActivityResultLauncher<Intent>) {
        launch {
            backupService.logout()
            if (backupService.authorize(launcher)) {
                openAddWalletThroughGoogleScreen()
            }
        }
    }

    fun openAddWalletThroughGoogleScreen() {
        launch {
            runCatching {
                backupService.getBackupAccounts()
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
}
