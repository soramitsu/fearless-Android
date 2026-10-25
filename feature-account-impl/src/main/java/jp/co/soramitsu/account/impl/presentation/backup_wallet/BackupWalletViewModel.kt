package jp.co.soramitsu.account.impl.presentation.backup_wallet

import android.app.Activity
import android.content.Intent
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.presentation.account.capturedPortfolioDetailFlow
import jp.co.soramitsu.account.api.presentation.account.CapturedPortfolioDetail
import jp.co.soramitsu.account.api.domain.model.supportedEcosystemWithIconAddress
import jp.co.soramitsu.account.api.presentation.account.withAllHoldings
import jp.co.soramitsu.account.api.presentation.backup.GoogleBackupIssue
import jp.co.soramitsu.account.api.presentation.backup.showGoogleBackupIssue
import jp.co.soramitsu.account.api.presentation.backup.toGoogleBackupIssue
import jp.co.soramitsu.account.impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.account.impl.domain.account.details.AccountInChain
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.model.BackupOrigin
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import jp.co.soramitsu.account.api.presentation.create_backup_password.SaveBackupPayload

@HiltViewModel
class BackupWalletViewModel @Inject constructor(
    private val accountRouter: AccountRouter,
    val savedStateHandle: SavedStateHandle,
    private val accountInteractor: AccountInteractor,
    private val accountDetailsInteractor: AccountDetailsInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val totalBalanceUseCase: TotalBalanceUseCase,
    private val resourceManager: ResourceManager
) : BaseViewModel(), BackupWalletCallback {

    private val walletId = savedStateHandle.get<Long>(BackupWalletDialog.ACCOUNT_ID_KEY)!!
    private val wallet = flowOf {
        accountInteractor.getMetaAccount(walletId)
    }

    // A build that cannot sign in to Google offers only the other backup options on this screen.
    val isAllowGoogleBackupFlow = wallet.map { wallet ->
        accountInteractor.isGoogleBackupAvailable() &&
            wallet.supportedEcosystemWithIconAddress().keys.any {
                it in listOf(WalletEcosystem.Substrate, WalletEcosystem.Ethereum)
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val supportedBackupTypes =
        flowOf { accountInteractor.getSupportedBackupTypes(walletId) }
    private val googleBackupAddressFlow =
        flowOf { accountInteractor.googleBackupAddressForWallet(walletId) }

    val requestGoogleAuth = MutableSharedFlow<Event<Unit>>()
    private val refresh = MutableSharedFlow<Event<Unit>>()
    private val isAuthedToGoogle = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val googleBackupType = refresh.flatMapLatest {
        googleBackupAddressFlow.map { backupAddress ->

            // Only copies under the wallet's own address: the address it was imported from can name
            // another account's backup, and "Delete" removes what this check finds.
            val appBackupCopies = try {
                accountInteractor.getGoogleBackupCopies(walletId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showGoogleBackupCheckIssue(e.toGoogleBackupIssue())
                return@map null
            }
            isAuthedToGoogle.value = true

            // Extension backups only decide how "delete" is answered, so they never fail the check.
            val webBackupAccountAddresses = try {
                accountInteractor.getExtensionGoogleBackups().map { it.address }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }

            when {
                appBackupCopies.isNotEmpty() -> GoogleBackup(BackupOrigin.APP, appBackupCopies)
                backupAddress in webBackupAccountAddresses -> GoogleBackup(BackupOrigin.WEB)
                else -> null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: MutableStateFlow<BackupWalletState> = MutableStateFlow(BackupWalletState.Empty)

    init {
        viewModelScope.launch {
            checkIsWalletWithChainAccounts()
        }

        capturedPortfolioDetailFlow(totalBalanceUseCase.observe(walletId)) { ready ->
            addressIconGenerator.createAddressIcon(
                ready.meta.supportedEcosystemWithIconAddress(),
                AddressIconGenerator.SIZE_BIG
            )
        }.catch { failure ->
            if (failure is CancellationException) throw failure
            emit(CapturedPortfolioDetail(TotalBalance.Unavailable, null))
            showError(failure)
        }.onEach { captured ->
            val ready = captured.source as? TotalBalance.Ready
            val item = WalletItemViewState(
                id = walletId,
                title = ready?.meta?.name.orEmpty(),
                isSelected = false,
                walletIcon = captured.detail ?: R.drawable.ic_wallet
            ).withAllHoldings(captured.source, resourceManager)
            state.update { previous -> previous.copy(walletItem = item) }
        }.launchIn(viewModelScope)

        googleBackupType.onEach { googleBackupType ->
            state.update { prev ->
                prev.copy(isWalletSavedInGoogle = googleBackupType != null)
            }
        }.launchIn(viewModelScope)

        supportedBackupTypes.onEach { supportedBackupTypes ->
            state.update { prev ->
                prev.copy(
                    isMnemonicBackupSupported = supportedBackupTypes.contains(BackupAccountType.PASSPHRASE),
                    isSeedBackupSupported = supportedBackupTypes.contains(BackupAccountType.SEED),
                    isJsonBackupSupported = supportedBackupTypes.contains(BackupAccountType.JSON)
                )
            }
        }.launchIn(viewModelScope)

        isAuthedToGoogle.onEach { isAuthedToGoogle ->
            state.update { prev ->
                prev.copy(
                    isAuthedToGoogle = isAuthedToGoogle
                )
            }
        }.launchIn(viewModelScope)

        isAllowGoogleBackupFlow.onEach { isAllowed ->
            state.update { prev ->
                prev.copy(isGoogleBackupAllowed = isAllowed)
            }
        }.launchIn(viewModelScope)
    }

    private suspend fun checkIsWalletWithChainAccounts() {
        val chainProjections =
            accountDetailsInteractor.getChainProjectionsFlow(walletId).firstOrNull().orEmpty()
        val chainAccounts = chainProjections[AccountInChain.From.CHAIN_ACCOUNT].orEmpty()

        if (chainAccounts.isEmpty()) {
            requestGoogleAuth.emit(Event(Unit))
        } else {
            val message = if (chainAccounts.size == 1) {
                val singleChainAccount = chainAccounts[0]
                val chainName = singleChainAccount.chain.name
                val address = singleChainAccount.projection?.address.orEmpty()
                resourceManager.getString(
                    R.string.backup_wallet_replace_accounts_alert,
                    chainName,
                    address
                )
            } else {
                resourceManager.getString(R.string.backup_wallet_replace_several_alert)
            }

            showError(
                title = resourceManager.getString(R.string.common_warning),
                message = message,
                positiveButtonText = resourceManager.getString(R.string.backup_chain_account),
                positiveClick = {
                    accountRouter.openAccountDetails(walletId)
                },
                onBackClick = {
                    launch {
                        requestGoogleAuth.emit(Event(Unit))
                    }
                }
            )
        }
    }

    override fun onBackClick() {
        accountRouter.back()
    }

    override fun onShowMnemonicPhraseClick() {
        val destination = accountRouter.getExportMnemonicDestination(
            walletId,
            polkadotChainId,
            isExportWallet = true
        )
        accountRouter.withPinCodeCheckRequired(
            destination,
            pinCodeTitleRes = R.string.account_export
        )
    }

    override fun onShowRawSeedClick() {
        val destination =
            accountRouter.getExportSeedDestination(walletId, polkadotChainId, isExportWallet = true)
        accountRouter.withPinCodeCheckRequired(
            destination,
            pinCodeTitleRes = R.string.account_export
        )
    }

    override fun onExportJsonClick() {
        val destination = accountRouter.openExportJsonPasswordDestination(
            walletId,
            polkadotChainId,
            isExportWallet = true
        )
        accountRouter.withPinCodeCheckRequired(
            destination,
            pinCodeTitleRes = R.string.account_export
        )
    }

    override fun onDeleteGoogleBackupClick() {
        viewModelScope.launch {
            val googleBackup = googleBackupType.firstOrNull() ?: return@launch

            if (googleBackup.origin == BackupOrigin.WEB) {
                showError(
                    title = resourceManager.getString(R.string.common_warning),
                    message = resourceManager.getString(R.string.remove_backup_extension_error_message)
                )
            } else {
                // Every copy goes, including other devices' copies that may have another password.
                val copies = googleBackup.copies
                val message = if (copies.size > 1) {
                    resourceManager.getQuantityString(R.plurals.backup_wallet_delete_copies_alert_message, copies.size, copies.size)
                } else {
                    resourceManager.getString(R.string.backup_wallet_delete_alert_message)
                }
                showError(
                    title = resourceManager.getString(R.string.common_confirmation_title),
                    message = message,
                    positiveButtonText = resourceManager.getString(R.string.common_delete),
                    negativeButtonText = resourceManager.getString(R.string.common_cancel),
                    buttonsOrientation = LinearLayout.HORIZONTAL,
                    positiveClick = { deleteGoogleBackup(copies) }
                )
            }
        }
    }

    private fun deleteGoogleBackup(copies: List<BackupAccountMeta>) {
        viewModelScope.launch {
            try {
                accountInteractor.deleteGoogleBackupAccount(walletId, copies)
                refresh.emit(Event(Unit))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when (val issue = e.toGoogleBackupIssue()) {
                    GoogleBackupIssue.SIGN_IN_REQUIRED -> showSignInIssue(issue)
                    else -> showGoogleBackupIssue(
                        resourceManager,
                        issue,
                        positiveButtonRes = R.string.common_try_again,
                        positiveClick = { deleteGoogleBackup(copies) },
                        negativeButtonRes = R.string.common_close
                    )
                }
            }
        }
    }

    override fun onGoogleBackupClick() {
        if (isAuthedToGoogle.value) {
            openCreateBackupPasswordDialog()
        } else {
            requestGoogleSignIn()
        }
    }

    private fun requestGoogleSignIn() {
        // The screen collects this without a buffer, so tryEmit would drop the request.
        launch {
            requestGoogleAuth.emit(Event(Unit))
        }
    }

    private fun showGoogleBackupCheckIssue(issue: GoogleBackupIssue) {
        when (issue) {
            GoogleBackupIssue.SIGN_IN_REQUIRED -> showSignInIssue(issue)
            // The phrase, seed and JSON options on this screen remain.
            GoogleBackupIssue.UNAVAILABLE -> showGoogleBackupIssue(resourceManager, issue)
            else -> showGoogleBackupIssue(
                resourceManager,
                issue,
                positiveButtonRes = R.string.common_try_again,
                positiveClick = ::checkIsWalletBackedUpToGoogle,
                negativeButtonRes = R.string.common_close
            )
        }
    }

    private fun showSignInIssue(issue: GoogleBackupIssue) {
        showGoogleBackupIssue(
            resourceManager,
            issue,
            positiveButtonRes = R.string.google_backup_sign_in,
            positiveClick = ::requestGoogleSignIn,
            negativeButtonRes = R.string.common_close
        )
    }


    override fun onGoogleSignInSuccess() {
        checkIsWalletBackedUpToGoogle()
    }

    private fun checkIsWalletBackedUpToGoogle() {
        runCatching {
            viewModelScope.launch {
                refresh.emit(Event(Unit))
            }
        }
    }

    fun authorizeGoogle(launcher: ActivityResultLauncher<Intent>) {
        viewModelScope.launch {
            try {
                if (accountInteractor.authorizeGoogleBackup(launcher)) {
                    checkIsWalletBackedUpToGoogle()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showGoogleBackupCheckIssue(e.toGoogleBackupIssue())
            }
        }
    }

    private fun openCreateBackupPasswordDialog() {
        val payload = SaveBackupPayload(
            walletId = walletId,
            addAccountPayload = null
        )
        accountRouter.openCreateBackupPasswordDialogWithResult(payload)
            .onEach { resultCode ->
                if (resultCode == Activity.RESULT_OK) {
                    checkIsWalletBackedUpToGoogle()
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onGoogleSignInFailed(result: GoogleSignInResult) {
        // Backing out keeps "Back up to Google" on this screen, which signs in again.
        val issue = result.toGoogleBackupIssue() ?: return
        showGoogleBackupCheckIssue(issue)
    }

    private data class GoogleBackup(
        val origin: BackupOrigin,
        // The app backups "Delete" removes; empty for an extension backup, which it cannot remove.
        val copies: List<BackupAccountMeta> = emptyList()
    )
}
