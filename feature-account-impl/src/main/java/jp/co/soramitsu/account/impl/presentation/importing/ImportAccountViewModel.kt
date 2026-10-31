package jp.co.soramitsu.account.impl.presentation.importing

import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.api.domain.model.ImportMode.Google
import jp.co.soramitsu.account.api.domain.model.ImportMode.Json
import jp.co.soramitsu.account.api.domain.model.ImportMode.MnemonicPhrase
import jp.co.soramitsu.account.api.domain.model.ImportMode.RawSeed
import jp.co.soramitsu.account.impl.domain.recovery.WalletKeyRecovery
import jp.co.soramitsu.account.impl.domain.recovery.WalletRecoveryIdentityMismatch
import jp.co.soramitsu.account.impl.domain.substratePasswordAccounts
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.account.impl.presentation.importing.ImportAccountFragment.Companion.IMPORT_ACCOUNT_TYPE_KEY
import jp.co.soramitsu.account.impl.presentation.importing.ImportAccountFragment.Companion.IMPORT_MODE_KEY
import jp.co.soramitsu.account.impl.presentation.importing.ImportAccountFragment.Companion.IMPORT_WALLET_ID_KEY
import jp.co.soramitsu.account.impl.presentation.importing.source.model.FileRequester
import jp.co.soramitsu.account.impl.presentation.importing.source.model.ImportError
import jp.co.soramitsu.account.impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.JsonImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.MnemonicImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.RawSeedImportSource
import jp.co.soramitsu.account.impl.presentation.mnemonic.backup.exceptions.NotValidDerivationPath
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.switchMap
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.core.crypto.SubstratePasswordRule
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import javax.inject.Inject

@HiltViewModel
class ImportAccountViewModel @Inject constructor(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val resourceManager: ResourceManager,
    private val cryptoTypeChooserMixin: CryptoTypeChooserMixin,
    private val clipboardManager: ClipboardManager,
    private val fileReader: FileReader,
    private val keyRecovery: WalletKeyRecovery,
    private val pinRecovery: jp.co.soramitsu.account.impl.domain.recovery.WalletPinRecovery,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(),
    CryptoTypeChooserMixin by cryptoTypeChooserMixin {

    val recoveryTarget = savedStateHandle.get<RecoveryTarget>(ImportAccountFragment.IMPORT_REBIND_KEY)
    val isRebinding = recoveryTarget != null
    val pinRecoveryChallenge = savedStateHandle.get<String>(ImportAccountFragment.PIN_RECOVERY_CHALLENGE)
    val recoveryError = MutableLiveData<String?>(null)
    private var recoveryJob: Job? = null
    val walletId = savedStateHandle.get<Long?>(IMPORT_WALLET_ID_KEY)
    val initialWalletEcosystem = savedStateHandle.get<WalletEcosystem>(IMPORT_ACCOUNT_TYPE_KEY)
    private val initialImportMode = savedStateHandle[IMPORT_MODE_KEY] ?: MnemonicPhrase

    private val _showEthAccountsDialog = MutableLiveData<Event<Unit>>()
    val showEthAccountsDialog: LiveData<Event<Unit>> = _showEthAccountsDialog

    // Words whose path password an earlier browser extension read differently open more than one
    // account; the import waits for the user's pick.
    private val _substratePasswordChooserEvent = MutableLiveData<Event<Payload<SubstratePasswordAccountModel>>>()
    val substratePasswordChooserEvent: LiveData<Event<Payload<SubstratePasswordAccountModel>>> = _substratePasswordChooserEvent
    private var pendingImportWithEth = true

    private val _blockchainTypeLiveData = MutableLiveData<WalletEcosystem>(initialWalletEcosystem)
    val blockchainLiveData: LiveData<WalletEcosystem> = _blockchainTypeLiveData

    val nameLiveData = MutableLiveData("")

    private val _showSourceChooserLiveData = MutableLiveData<Event<Payload<ImportSource>>>()
    val showSourceSelectorChooserLiveData: LiveData<Event<Payload<ImportSource>>> = _showSourceChooserLiveData

    val substrateDerivationPathLiveData = MutableLiveData<String>()
    val ethereumDerivationPathLiveData = MutableLiveData<String>()

    private val substrateDerivationPathRegex = Regex("(//?[^/]+)*(///[^/]+)?")

    val sourceTypes = provideSourceType()
    private val initialSelectedSourceType: ImportSource
        get() {
            return when (initialImportMode) {
                MnemonicPhrase -> sourceTypes.firstOrNull { it is MnemonicImportSource }
                RawSeed -> sourceTypes.firstOrNull { it is RawSeedImportSource }
                Json -> sourceTypes.firstOrNull { it is JsonImportSource }
                Google -> null
                ImportMode.Preinstalled -> null
            } ?: sourceTypes.first()
        }
    private val _selectedSourceTypeLiveData = MutableLiveData(initialSelectedSourceType)
    val selectedSourceLiveData: LiveData<ImportSource> = _selectedSourceTypeLiveData

    private val sourceTypeValid = _selectedSourceTypeLiveData.switchMap(ImportSource::validationLiveData)

    private val importInProgressLiveData = MutableLiveData(false)

    private val nextButtonEnabledLiveData = sourceTypeValid.combine(nameLiveData) { sourceTypeValid, name ->
        sourceTypeValid && (isRebinding || name.isNotEmpty())
    }

    val nextButtonState = nextButtonEnabledLiveData.combine(importInProgressLiveData) { enabled, inProgress ->
        when {
            inProgress -> ButtonState.PROGRESS
            enabled -> ButtonState.NORMAL
            else -> ButtonState.DISABLED
        }
    }

    private var substrateSeed: String? = null
    private var ethSeed: String? = null
    private var substrateJson: String? = null
    private var ethJson: String? = null

    fun homeButtonClicked() {
        if (isRebinding) router.back() else router.backToWelcomeScreen()
    }

    fun openSourceChooserClicked() {
        _selectedSourceTypeLiveData.value.let {
            _showSourceChooserLiveData.value = Event(Payload(sourceTypes, it))
        }
    }

    fun sourceTypeChanged(it: ImportSource) {
        _selectedSourceTypeLiveData.postValue(it)
    }

    fun nextClicked() {
        if (isRebinding) {
            restoreKey()
            return
        }
        val isSubstrateDerivationPathValid = substrateDerivationPathLiveData.value?.matches(substrateDerivationPathRegex)
        if (isSubstrateDerivationPathValid == false) {
            showError(NotValidDerivationPath(resourceManager))
            return
        }
        val source = _selectedSourceTypeLiveData.value
        when {
            source is MnemonicImportSource -> {
                import(withEth = true)
            }
            source is RawSeedImportSource && _blockchainTypeLiveData.value == WalletEcosystem.Substrate -> {
                source.rawSeedLiveData.value?.let {
                    substrateSeed = it
                    _showEthAccountsDialog.value = Event(Unit)
                }
            }
            source is RawSeedImportSource && _blockchainTypeLiveData.value == WalletEcosystem.Ethereum -> {
                source.rawSeedLiveData.value?.let {
                    ethSeed = it
                    import(withEth = true)
                }
            }
            source is JsonImportSource && _blockchainTypeLiveData.value == WalletEcosystem.Substrate -> {
                source.jsonContentLiveData.value?.let {
                    substrateJson = it
                    _showEthAccountsDialog.value = Event(Unit)
                }
            }
            source is JsonImportSource && _blockchainTypeLiveData.value == WalletEcosystem.Ethereum -> {
                source.jsonContentLiveData.value?.let {
                    ethJson = it
                    import(withEth = true)
                }
            }
        }
    }

    private fun restoreKey() {
        if (recoveryJob?.isActive == true) return
        val target = recoveryTarget ?: return
        val source = selectedSourceLiveData.value ?: return
        val mode: ImportMode
        val material: String
        val password: String
        when (source) {
            is MnemonicImportSource -> {
                mode = MnemonicPhrase
                material = source.mnemonicContentLiveData.value.orEmpty()
                password = ""
            }
            is RawSeedImportSource -> {
                mode = RawSeed
                material = source.rawSeedLiveData.value.orEmpty()
                password = ""
            }
            is JsonImportSource -> {
                mode = Json
                material = source.jsonContentLiveData.value.orEmpty()
                password = source.passwordLiveData.value.orEmpty()
            }
            else -> return
        }
        val path = if (target.ecosystem() == WalletEcosystem.Ethereum) ethereumDerivationPathLiveData.value.orEmpty()
            else substrateDerivationPathLiveData.value.orEmpty()
        importInProgressLiveData.value = true
        recoveryError.value = null
        recoveryJob = viewModelScope.launch {
            try {
                if (pinRecoveryChallenge == null) keyRecovery.restore(target.metaId, target.scope(), mode, material, path, password)
                else pinRecovery.verifyWallet(pinRecoveryChallenge, target, mode, material, path, password)
                clearRecoveryInput()
                showMessage(resourceManager.getString(if (pinRecoveryChallenge == null) R.string.wallet_recovery_restored else R.string.pin_recovery_verified))
                router.back()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                recoveryError.value = resourceManager.getString(
                    if (failure is WalletRecoveryIdentityMismatch) R.string.wallet_recovery_different_key
                    else R.string.wallet_recovery_restore_failed
                )
            } finally {
                importInProgressLiveData.value = false
            }
        }
    }

    /** Leaving an authenticated recovery screen discards input and pending derivation. */
    fun recoveryScreenStopped() {
        if (!isRebinding) return
        recoveryJob?.cancel()
        clearRecoveryInput()
    }

    private fun clearRecoveryInput() {
        sourceTypes.forEach { source ->
            when (source) {
                is MnemonicImportSource -> source.mnemonicContentLiveData.value = ""
                is RawSeedImportSource -> source.rawSeedLiveData.value = ""
                is JsonImportSource -> source.clearRecoveryInput()
            }
        }
        substrateDerivationPathLiveData.value = ""
        ethereumDerivationPathLiveData.value = ""
    }

    private fun import(withEth: Boolean, substratePasswordRule: SubstratePasswordRule? = null) {
        val sourceType = _selectedSourceTypeLiveData.value ?: return
        importInProgressLiveData.value = true

        val cryptoType = selectedEncryptionTypeLiveData.value!!.cryptoType
        val substrateDerivationPath = substrateDerivationPathLiveData.value.orEmpty()
        val ethereumDerivationPath = ethereumDerivationPathLiveData.value.orEmpty().ifEmpty { BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH }
        val name = nameLiveData.value.orEmpty()

        viewModelScope.launch {
            if (substratePasswordRule == null) {
                val accounts = substratePasswordChoice(sourceType, substrateDerivationPath, cryptoType)
                if (accounts.size > 1) {
                    pendingImportWithEth = withEth
                    _substratePasswordChooserEvent.value = Event(Payload(accounts, accounts.first()))
                    importInProgressLiveData.value = false
                    return@launch
                }
            }
            val passwordRule = substratePasswordRule ?: SubstratePasswordRule.NFKD
            val result = import(sourceType, name, substrateDerivationPath, ethereumDerivationPath, cryptoType, passwordRule)

            if (result.isSuccess) {
                continueBasedOnCodeStatus()
            } else {
                handleCreateAccountError(result.requireException())
            }

            importInProgressLiveData.value = false
        }
    }

    fun substratePasswordAccountChosen(account: SubstratePasswordAccountModel) {
        import(pendingImportWithEth, account.rule)
    }

    // The accounts a new wallet's words and path open, one per rule that reads the path's password
    // differently; empty for any other import, and when the words cannot be read (the import says why).
    private suspend fun substratePasswordChoice(
        sourceType: ImportSource,
        substrateDerivationPath: String,
        cryptoType: CryptoType
    ): List<SubstratePasswordAccountModel> {
        if (sourceType !is MnemonicImportSource || walletId != null || blockchainLiveData.value == WalletEcosystem.Ton) {
            return emptyList()
        }
        if (!substrateDerivationPath.contains("///")) return emptyList()
        val mnemonic = sourceType.mnemonicContentLiveData.value ?: return emptyList()
        val accounts = runCatching {
            withContext(Dispatchers.Default) { substratePasswordAccounts(mnemonic, substrateDerivationPath, cryptoType) }
        }.getOrNull().orEmpty()
        return accounts.map { account ->
            val title = when (account.rule) {
                SubstratePasswordRule.NFKD -> R.string.substrate_password_choice_nfkd
                SubstratePasswordRule.NFC -> R.string.substrate_password_choice_nfc
                SubstratePasswordRule.AS_TYPED -> R.string.substrate_password_choice_as_typed
            }
            SubstratePasswordAccountModel(account.rule, resourceManager.getString(title), account.address)
        }
    }

    fun systemCallResultReceived(requestCode: Int, intent: Intent) {
        val selectedSource = _selectedSourceTypeLiveData.value

        if (selectedSource is FileRequester) {
            val currentRequestCode = selectedSource.chooseJsonFileEvent.value!!.peekContent()

            if (requestCode == currentRequestCode) {
                selectedSource.fileChosen(intent.data!!)
            }
        }
    }

    private suspend fun continueBasedOnCodeStatus() {
        if (interactor.isCodeSet()) {
            router.openMain()
        } else {
            router.openCreatePincode()
        }
    }

    private fun handleCreateAccountError(throwable: Throwable) {
        var errorMessage = _selectedSourceTypeLiveData.value?.handleError(throwable)

        if (errorMessage == null) {
            errorMessage = when (throwable) {
                is AccountAlreadyExistsException -> ImportError(
                    titleRes = R.string.account_add_already_exists_message,
                    messageRes = R.string.account_error_try_another_one
                )
                else -> ImportError()
            }
        }

        val title = resourceManager.getString(errorMessage.titleRes)
        val message = resourceManager.getString(errorMessage.messageRes)

        showError(title, message)
    }

    private fun provideSourceType(): List<ImportSource> {
        return listOf(
            MnemonicImportSource(),
            RawSeedImportSource(),
            JsonImportSource(
                nameLiveData,
                cryptoTypeChooserMixin.selectedEncryptionTypeLiveData,
                interactor,
                resourceManager,
                clipboardManager,
                fileReader,
                viewModelScope,
                recoveryMode = isRebinding
            )
        )
    }

    private suspend fun import(
        sourceType: ImportSource,
        name: String,
        substrateDerivationPath: String,
        ethereumDerivationPath: String,
        cryptoType: CryptoType,
        substratePasswordRule: SubstratePasswordRule
    ): Result<Any> {
        return when (sourceType) {
            is MnemonicImportSource -> {
                val payload = if (blockchainLiveData.value == WalletEcosystem.Ton) {
                    AddAccountPayload.Ton(
                        accountName = name,
                        mnemonic = sourceType.mnemonicContentLiveData.value!!,
                        isBackedUp = true,
                    )
                } else {
                    if (walletId == null) {
                        AddAccountPayload.SubstrateOrEvm(
                            accountName = name,
                            mnemonic = sourceType.mnemonicContentLiveData.value!!,
                            encryptionType = cryptoType,
                            substrateDerivationPath = substrateDerivationPath,
                            ethereumDerivationPath = ethereumDerivationPath,
                            googleBackupAddress = null,
                            isBackedUp = true,
                            substratePasswordRule = substratePasswordRule
                        )
                    } else {
                        AddAccountPayload.AdditionalEvm(
                            walletId = walletId,
                            accountName = name,
                            mnemonic = sourceType.mnemonicContentLiveData.value!!,
                            ethereumDerivationPath = ethereumDerivationPath,
                            isBackedUp = true
                        )
                    }
                }
                interactor.createAccount(payload)
            }
            is RawSeedImportSource -> interactor.importFromSeed(
                walletId = walletId,
                substrateSeed = substrateSeed,
                username = name,
                derivationPath = substrateDerivationPath,
                selectedEncryptionType = cryptoType,
                ethSeed = ethSeed,
                googleBackupAddress = null
            )
            is JsonImportSource -> interactor.importFromJson(
                walletId = walletId,
                json = substrateJson,
                password = sourceType.passwordLiveData.value!!,
                name = name,
                ethJson = ethJson,
                googleBackupAddress = null
            )
        }
    }

    fun onAddEthAccountConfirmed() {
        _blockchainTypeLiveData.value = WalletEcosystem.Ethereum
        (_selectedSourceTypeLiveData.value as? RawSeedImportSource)?.rawSeedLiveData?.value = ""
        (_selectedSourceTypeLiveData.value as? JsonImportSource)?.apply {
            jsonContentLiveData.value = ""
            passwordLiveData.value = ""
        }
    }

    fun onAddEthAccountDeclined() {
        import(false)
    }
}
