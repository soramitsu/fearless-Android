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
import jp.co.soramitsu.account.api.presentation.account.withAllHoldings
import jp.co.soramitsu.account.api.presentation.backup.GoogleBackupIssue
import jp.co.soramitsu.account.api.presentation.backup.showGoogleBackupIssue
import jp.co.soramitsu.account.api.presentation.backup.toGoogleBackupIssue
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.domain.model.supportedEcosystemWithIconAddress
import jp.co.soramitsu.account.api.presentation.account.capturedPortfolioListFlow
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletSelectorViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SelectWalletViewModel @Inject constructor(
    accountRepository: AccountRepository,
    addressIconGenerator: AddressIconGenerator,
    private val accountInteractor: AccountInteractor,
    private val nomisScoreInteractor: NomisScoreInteractor,
    private val router: WalletRouter,
    private val getTotalBalance: TotalBalanceUseCase,
    private val resourceManager: ResourceManager,
    private val pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario
) : BaseViewModel() {

    private val walletItemsFlow = combine(
        capturedPortfolioListFlow(
            accountRepository.lightMetaAccountsFlow().map { wallets -> wallets.map { it.id } },
            balances = { metaId -> getTotalBalance.observe(metaId) },
            resolveDetail = { ready ->
                addressIconGenerator.createAddressIcon(
                    ready.meta.supportedEcosystemWithIconAddress(),
                    AddressIconGenerator.SIZE_BIG
                )
            }
        ),
        nomisScoreInteractor.observeNomisScores()
            .onStart { emit(emptyList()) }
            .catch { failure ->
                if (failure is CancellationException) throw failure
                emit(emptyList())
            }
    ) { rows, scores ->
        rows.map { row ->
            val ready = row.portfolio.source as? TotalBalance.Ready
            WalletItemViewState(
                id = row.metaId,
                title = ready?.meta?.name.orEmpty(),
                isSelected = ready?.meta?.isSelected == true,
                walletIcon = row.portfolio.detail ?: R.drawable.ic_wallet,
                score = if (ready == null) null else scores.firstOrNull { it.metaId == row.metaId }?.score
            ).withAllHoldings(row.portfolio.source, resourceManager)
        }
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        emit(emptyList())
        showError(failure)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val selectedWalletId = MutableStateFlow<Long?>(null)
    val googleAuthorizeLiveData = MutableLiveData<Event<Unit>>()
    val importPreInstalledWalletLiveData = MutableLiveData<Event<Unit>>()

    val state = combine(
        walletItemsFlow,
        selectedWalletId
    ) { walletItems, selectedId ->
        WalletSelectorViewState(
            wallets = walletItems,
            selectedWallet = walletItems.firstOrNull { it.id == selectedId } ?: walletItems.firstOrNull { it.isSelected }
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
            selectedWalletId.value = item.id
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
        if (!accountInteractor.isGoogleBackupAvailable()) {
            showGoogleSignInIssue(GoogleBackupIssue.UNAVAILABLE)
            return
        }

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
                showGoogleSignInIssue(it.toGoogleBackupIssue())
            }.onSuccess { backupAccounts ->
                router.openImportRemoteWalletDialog()
            }
        }
    }

    fun onGoogleSignInFailed(result: GoogleSignInResult) {
        val issue = result.toGoogleBackupIssue() ?: return
        showGoogleSignInIssue(issue)
    }

    // Adding a wallet also offers the recovery phrase, raw seed and JSON imports.
    private fun showGoogleSignInIssue(issue: GoogleBackupIssue) {
        if (issue.isRetryable) {
            showGoogleBackupIssue(
                resourceManager,
                issue,
                positiveButtonRes = R.string.common_try_again,
                positiveClick = ::openAddWalletThroughGoogleScreen,
                negativeButtonRes = R.string.google_backup_import_another_way,
                negativeClick = ::addNewWallet
            )
        } else {
            showGoogleBackupIssue(
                resourceManager,
                issue,
                positiveButtonRes = R.string.google_backup_import_another_way,
                positiveClick = ::addNewWallet,
                negativeButtonRes = R.string.common_close
            )
        }
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
