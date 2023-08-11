package jp.co.soramitsu.account.impl.presentation.backup_wallet

import android.content.Intent
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.presentation.create_backup_password.CreateBackupPasswordPayload
import jp.co.soramitsu.account.impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.account.impl.domain.account.details.AccountInChain
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.model.BackupOrigin
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.MnemonicCreator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BackupWalletViewModel @Inject constructor(
    private val accountRouter: AccountRouter,
    val savedStateHandle: SavedStateHandle,
    private val accountInteractor: AccountInteractor,
    private val accountDetailsInteractor: AccountDetailsInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val totalBalanceUseCase: TotalBalanceUseCase,
    private val resourceManager: ResourceManager,
    private val backupService: BackupService
) : BaseViewModel(), BackupWalletCallback {

    private val walletId = savedStateHandle.get<Long>(BackupWalletDialog.ACCOUNT_ID_KEY)!!
    private val wallet = flowOf {
        accountInteractor.getMetaAccount(walletId)
    }
    private val walletItem = wallet
        .map { wallet ->

            val icon = addressIconGenerator.createAddressIcon(
                wallet.substrateAccountId,
                AddressIconGenerator.SIZE_BIG
            )

            val balanceModel = totalBalanceUseCase(walletId)

            WalletItemViewState(
                id = walletId,
                title = wallet.name,
                isSelected = false,
                walletIcon = icon,
                balance = balanceModel.balance.formatFiat(balanceModel.fiatSymbol),
                changeBalanceViewState = ChangeBalanceViewState(
                    percentChange = balanceModel.rateChange?.formatAsChange().orEmpty(),
                    fiatChange = balanceModel.balanceChange.abs().formatFiat(balanceModel.fiatSymbol)
                )
            )
        }
    private val supportedBackupTypes = flowOf { accountInteractor.getSupportedBackupTypes(walletId) }
    private val googleBackupAddressFlow = flowOf { accountInteractor.googleBackupAddressForWallet(walletId) }

    val requestGoogleAuth = MutableSharedFlow<Event<Unit>>()
    private val refresh = MutableSharedFlow<Event<Unit>>()
    private val isAuthedToGoogle = MutableStateFlow(false)

    private val googleBackupType = refresh.flatMapLatest {
        googleBackupAddressFlow.map { backupAddress ->

            val backupAccountAddresses = kotlin.runCatching {
                backupService.getBackupAccounts().map { it.address }
            }.onSuccess {
                isAuthedToGoogle.value = true
            }.onFailure {
                return@map null
            }.getOrNull().orEmpty()

            val webBackupAccountAddresses = backupService.getWebBackupAccounts().map { it.address }

            when (backupAddress) {
                in backupAccountAddresses -> BackupOrigin.APP
                in webBackupAccountAddresses -> BackupOrigin.WEB
                else -> null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state = combine(
        walletItem,
        googleBackupType,
        supportedBackupTypes,
        isAuthedToGoogle
    ) { walletItem, googleBackupType, supportedBackupTypes, isAuthedToGoogle ->
        BackupWalletState(
            walletItem = walletItem,
            isAuthedToGoogle = isAuthedToGoogle,
            isWalletSavedInGoogle = googleBackupType != null,
            isMnemonicBackupSupported = supportedBackupTypes.contains(BackupAccountType.PASSPHRASE),
            isSeedBackupSupported = supportedBackupTypes.contains(BackupAccountType.SEED),
            isJsonBackupSupported = supportedBackupTypes.contains(BackupAccountType.JSON)
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BackupWalletState.Empty)

    init {
        launch {
            checkIsWalletWithChainAccounts()
        }
    }

    private suspend fun checkIsWalletWithChainAccounts() {
        val chainProjections = accountDetailsInteractor.getChainProjectionsFlow(walletId).firstOrNull().orEmpty()
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
        val destination = accountRouter.getExportMnemonicDestination(walletId, polkadotChainId, isExportWallet = true)
        accountRouter.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
    }

    override fun onShowRawSeedClick() {
        val destination = accountRouter.getExportSeedDestination(walletId, polkadotChainId, isExportWallet = true)
        accountRouter.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
    }

    override fun onExportJsonClick() {
        val destination = accountRouter.openExportJsonPasswordDestination(walletId, polkadotChainId, isExportWallet = true)
        accountRouter.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
    }

    override fun onDeleteGoogleBackupClick() {
        launch {
            val backupOrigin = googleBackupType.firstOrNull()

            if (backupOrigin == BackupOrigin.WEB) {
                showError(
                    title = resourceManager.getString(R.string.common_warning),
                    message = resourceManager.getString(R.string.remove_backup_extension_error_message)
                )
            } else {
                showError(
                    title = resourceManager.getString(R.string.common_confirmation_title),
                    message = resourceManager.getString(R.string.backup_wallet_delete_alert_message),
                    positiveButtonText = resourceManager.getString(R.string.common_delete),
                    negativeButtonText = resourceManager.getString(R.string.common_cancel),
                    buttonsOrientation = LinearLayout.HORIZONTAL,
                    positiveClick = ::deleteGoogleBackup
                )
            }
        }
    }

    private fun deleteGoogleBackup() {
        launch {
            googleBackupAddressFlow.firstOrNull()?.let { address ->
                runCatching {
                    backupService.deleteBackupAccount(address)
                    accountInteractor.updateWalletOnGoogleBackupDelete(walletId)
                    refresh.emit(Event(Unit))
                }.onFailure {
                    showError("DeleteGoogleBackup error:\n${it.message}")
                }
            }
        }
    }

    override fun onGoogleBackupClick() {
        if (isAuthedToGoogle.value) {
            openCreateBackupPasswordDialog()
        } else {
            requestGoogleAuth.tryEmit(Event(Unit))
        }
    }


    override fun onGoogleSignInSuccess() {
        checkIsWalletBackedUpToGoogle()
    }

    private fun checkIsWalletBackedUpToGoogle() {
        launch {
            refresh.emit(Event(Unit))
        }
    }

    fun authorizeGoogle(launcher: ActivityResultLauncher<Intent>) {
        launch {
            try {
                backupService.logout()
                if (backupService.authorize(launcher)) {
                    checkIsWalletBackedUpToGoogle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError(e)
            }
        }
    }

    private fun openCreateBackupPasswordDialog() {
        launch {
            val secrets = accountInteractor.getMetaAccountSecrets(walletId) ?: error("There are no secrets for walletId: $walletId")
            val entropy = secrets[MetaAccountSecrets.Entropy]
            val substrateDerivationPath = secrets[MetaAccountSecrets.SubstrateDerivationPath]
            val ethereumDerivationPath = secrets[MetaAccountSecrets.EthereumDerivationPath]
            val payload = CreateBackupPasswordPayload(
                walletId = walletId,
                mnemonic = entropy?.let { MnemonicCreator.fromEntropy(it).words }.orEmpty(),
                accountName = wallet.first().name,
                cryptoType = wallet.first().substrateCryptoType,
                substrateDerivationPath = substrateDerivationPath.orEmpty(),
                ethereumDerivationPath = ethereumDerivationPath.orEmpty(),
                createAccount = false
            )
            accountRouter.openCreateBackupPasswordDialog(payload)
        }
    }

    override fun onGoogleLoginError(message: String) {
        showError("GoogleLoginError\n$message")
    }
}
