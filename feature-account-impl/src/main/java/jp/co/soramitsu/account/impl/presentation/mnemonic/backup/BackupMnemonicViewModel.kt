package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.app.Activity
import android.content.Intent
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.presentation.create_backup_password.SaveBackupPayload
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.account.impl.presentation.mnemonic.backup.exceptions.NotValidDerivationPath
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload.CreateExtras
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.MnemonicWordModel
import jp.co.soramitsu.common.compose.component.mapMnemonicToMnemonicWords
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.shared_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.Mnemonic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class BackupMnemonicViewModel @Inject constructor(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val cryptoTypeChooserMixin: CryptoTypeChooserMixin,
    savedStateHandle: SavedStateHandle,
    private val backupService: BackupService,
    private val resourceManager: ResourceManager
) : BaseViewModel(),
    BackupMnemonicCallback,
    CryptoTypeChooserMixin by cryptoTypeChooserMixin {

    private val payload =
        savedStateHandle.get<BackupMnemonicPayload>(BackupMnemonicScreenKeys.PAYLOAD_KEY)!!
    val walletId = payload.walletId

    val isEthereumAccount = payload.accountTypes.contains(WalletEcosystem.Ethereum)
    val isSubstrateAccount = payload.accountTypes.contains(WalletEcosystem.Substrate)
    val isTonAccount = payload.accountTypes.contains(WalletEcosystem.Ton)
    val isSubstrateAndEthereumAccount = isSubstrateAccount && isEthereumAccount
    val isSubstrateOrEthereumAccount = isSubstrateAccount || isEthereumAccount

    val isShowAdvancedBlock =
        !payload.isFromGoogleBackup && isSubstrateOrEthereumAccount
    val isShowBackupWithGoogle =
        !payload.isFromGoogleBackup && walletId == null && isSubstrateOrEthereumAccount
    val isShowSkipButton = isTonAccount

    val mnemonic = flow {
        val mnemonicLength = if (isSubstrateOrEthereumAccount) {
            Mnemonic.Length.TWELVE
        } else {
            Mnemonic.Length.TWENTY_FOUR
        }
        emit(generateMnemonic(mnemonicLength))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val selectedEncryptionType = selectedEncryptionTypeLiveData.asFlow()
    private val substrateDerivationPath = MutableStateFlow("")
    private val ethereumDerivationPath = MutableStateFlow("")

    val state = combine(
        mnemonic,
        selectedEncryptionType,
        substrateDerivationPath,
        ethereumDerivationPath
    ) { mnemonic,
        selectedEncryptionType,
        substrateDerivationPath,
        ethereumDerivationPath ->
        BackupMnemonicState(
            mnemonicWords = mnemonic,
            selectedEncryptionType = selectedEncryptionType.name,
            accountTypes = payload.accountTypes,
            substrateDerivationPath = substrateDerivationPath,
            ethereumDerivationPath = ethereumDerivationPath,
            isFromGoogleBackup = payload.isFromGoogleBackup
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BackupMnemonicState.Empty)

    private val substrateDerivationPathRegex = Regex("(//?[^/]+)*(///[^/]+)?")

    private val _showInfoEvent = MutableLiveData<Event<Unit>>()
    val showInfoEvent: LiveData<Event<Unit>> = _showInfoEvent

    fun homeButtonClicked() {
        router.backToCreateAccountScreen()
    }

    fun infoClicked() {
        _showInfoEvent.value = Event(Unit)
    }

    override fun onNextClick(launcher: ActivityResultLauncher<Intent>) {
        viewModelScope.launch {
            val substrateDerivationPath = substrateDerivationPath.value
            val ethereumDerivationPath =
                ethereumDerivationPath.value.ifEmpty { BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH }

            if (payload.isFromGoogleBackup) {
                backupPhraseInGoogle(substrateDerivationPath, launcher)
                return@launch
            }

            openConfirmMnemonicOnCreate(substrateDerivationPath, ethereumDerivationPath)
        }
    }

    override fun onBackupWithGoogleClick(
        launcher: ActivityResultLauncher<Intent>
    ) {
        viewModelScope.launch {
            val substrateDerivationPath = substrateDerivationPath.value
            backupPhraseInGoogle(substrateDerivationPath, launcher)
        }
    }

    override fun onSubstrateDerivationPathChange(path: String) {
        substrateDerivationPath.value = path
    }

    override fun onEthereumDerivationPathChange(path: String) {
        ethereumDerivationPath.value = path
    }

    fun onNextClick(
        substrateDerivationPath: String,
        ethereumDerivationPath: String,
        launcher: ActivityResultLauncher<Intent>
    ) {
        viewModelScope.launch {
            if (payload.isFromGoogleBackup) {
                backupPhraseInGoogle(substrateDerivationPath, launcher)
                return@launch
            }

            openConfirmMnemonicOnCreate(substrateDerivationPath, ethereumDerivationPath)
        }
    }

    private fun openConfirmMnemonicOnCreate(
        substrateDerivationPath: String,
        ethereumDerivationPath: String
    ) {
        val cryptoTypeModel = selectedEncryptionTypeLiveData.value ?: return

        val mnemonicWords = mnemonic.value

        val mnemonic = mnemonicWords.map(MnemonicWordModel::word)

        val isSubstrateDerivationPathValid =
            substrateDerivationPath.matches(substrateDerivationPathRegex)
        if (isSubstrateDerivationPathValid.not()) {
            showError(NotValidDerivationPath(resourceManager))
            return
        }

        val createExtras = CreateExtras(
                payload.accountName,
                cryptoTypeModel.cryptoType,
                substrateDerivationPath,
                ethereumDerivationPath
            )

        val payload = ConfirmMnemonicPayload(
            mnemonic = mnemonic,
            metaId = walletId,
            createExtras = createExtras,
            accountTypes = payload.accountTypes
        )

        router.openConfirmMnemonicOnCreate(payload)
    }

    fun onGoogleBackupClick(
        substrateDerivationPath: String,
        launcher: ActivityResultLauncher<Intent>
    ) {
        viewModelScope.launch {
            backupPhraseInGoogle(substrateDerivationPath, launcher)
        }
    }

    private suspend fun backupPhraseInGoogle(
        substrateDerivationPath: String,
        launcher: ActivityResultLauncher<Intent>
    ) {
        val isSubstrateDerivationPathValid =
            substrateDerivationPath.matches(substrateDerivationPathRegex)
        if (isSubstrateDerivationPathValid.not()) {
            showError(NotValidDerivationPath(resourceManager))
            return
        }

        if (payload.isFromGoogleBackup.not()) {
            backupService.logout()
        }
        if (backupService.authorize(launcher)) {
            openCreateBackupPasswordDialog()
        }
    }

    override fun onGoogleSignInSuccess() {
        openCreateBackupPasswordDialog()
    }

    private fun openCreateBackupPasswordDialog() {
        val createAndSavePayload = SaveBackupPayload(
            walletId = null,
            addAccountPayload = getAddAccountPayload()
        )
        router.openCreateBackupPasswordDialogWithResult(createAndSavePayload)
            .onEach { resultCode ->
                if (resultCode == Activity.RESULT_OK) {
                    continueBasedOnCodeStatus()
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onGoogleLoginError(message: String) {
        showError("GoogleLoginError\n$message")
    }

    private suspend fun generateMnemonic(length: Mnemonic.Length): List<MnemonicWordModel> {
        val mnemonic = interactor.generateMnemonic(length)

        return withContext(Dispatchers.Default) {
            mapMnemonicToMnemonicWords(mnemonic)
        }
    }

    override fun onBackClick() {
        router.back()
    }

    fun skipClicked() {
        showError(
            title = resourceManager.getString(R.string.backup_not_backed_up_title),
            message = resourceManager.getString(R.string.backup_not_backed_up_message),
            positiveButtonText = resourceManager.getString(R.string.backup_not_backed_up_confirm),
            negativeButtonText = resourceManager.getString(R.string.common_cancel),
            buttonsOrientation = LinearLayout.HORIZONTAL,
            positiveClick = {
                proceed()
            }
        )
    }

    private fun proceed() {
        viewModelScope.launch {
            val result = createAccount()
            if (result.isSuccess) {
                continueBasedOnCodeStatus()
            } else {
                showError(result.requireException())
            }
        }
    }

    private fun getAddAccountPayload(): AddAccountPayload {
        val mnemonicWords = this@BackupMnemonicViewModel.mnemonic.value.map(MnemonicWordModel::word)
        val mnemonicString = mnemonicWords.joinToString(" ")

        return when {
            isSubstrateOrEthereumAccount -> {
                val cryptoTypeModel = selectedEncryptionTypeLiveData.value ?: error("There must be encryption type selected for substrate ecosystem")
                val substrateDerivationPath = substrateDerivationPath.value
                val ethereumDerivationPath =
                    ethereumDerivationPath.value.ifEmpty { BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH }
                AddAccountPayload.SubstrateOrEvm(
                    payload.accountName,
                    mnemonicString,
                    cryptoTypeModel.cryptoType,
                    substrateDerivationPath,
                    ethereumDerivationPath,
                    null,
                    false
                )
            }

            isTonAccount -> AddAccountPayload.Ton(
                payload.accountName,
                mnemonicString,
                false
            )

            else -> error("AccountType not specified")
        }
    }

    private suspend fun createAccount(): Result<Long> {
        val mnemonicWords = this@BackupMnemonicViewModel.mnemonic.value.map(MnemonicWordModel::word)
        val mnemonicString = mnemonicWords.joinToString(" ")

        val addAccountPayload = when {
            isSubstrateOrEthereumAccount -> {
                val cryptoTypeModel = selectedEncryptionTypeLiveData.value ?: return Result.failure(
                    IllegalStateException("There must be encryption type selected for substrate ecosystem")
                )
                val substrateDerivationPath = substrateDerivationPath.value
                val ethereumDerivationPath =
                    ethereumDerivationPath.value.ifEmpty { BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH }
                AddAccountPayload.SubstrateOrEvm(
                    payload.accountName,
                    mnemonicString,
                    cryptoTypeModel.cryptoType,
                    substrateDerivationPath,
                    ethereumDerivationPath,
                    null,
                    false
                )
            }

            isTonAccount -> AddAccountPayload.Ton(
                payload.accountName,
                mnemonicString,
                false
            )

            else -> return Result.failure(
                IllegalStateException("AccountType not specified")
            )
        }
        return interactor.createAccount(addAccountPayload)
    }

    private fun continueBasedOnCodeStatus() {
        launch(Dispatchers.Main) {
            if (interactor.isCodeSet()) {
                router.openMain()
            } else {
                router.openCreatePincode()
            }
        }
    }
}
