package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asFlow
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.presentation.create_backup_password.CreateBackupPasswordPayload
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
import jp.co.soramitsu.account.api.presentation.importing.importAccountType
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.account.impl.presentation.mnemonic.backup.exceptions.NotValidDerivationPath
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload.CreateExtras
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.MnemonicWordModel
import jp.co.soramitsu.common.compose.component.mapMnemonicToMnemonicWords
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

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

    private val payload = savedStateHandle.get<BackupMnemonicPayload>(BackupMnemonicScreenKeys.PAYLOAD_KEY)!!
    val isFromGoogleBackup = payload.isFromGoogleBackup

    val mnemonic = flow {
        emit(generateMnemonic())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val selectedEncryptionType = selectedEncryptionTypeLiveData.asFlow()
    private val accountType = MutableStateFlow(ImportAccountType.Substrate)
    private val substrateDerivationPath = MutableStateFlow("")
    private val ethereumDerivationPath = MutableStateFlow("")

    val state = combine(
        mnemonic,
        selectedEncryptionType,
        accountType,
        substrateDerivationPath,
        ethereumDerivationPath
    ) {
            mnemonic,
            selectedEncryptionType,
            accountType,
            substrateDerivationPath,
            ethereumDerivationPath ->
        BackupMnemonicState(
            mnemonicWords = mnemonic,
            selectedEncryptionType = selectedEncryptionType.name,
            accountType = accountType,
            substrateDerivationPath = substrateDerivationPath,
            ethereumDerivationPath = ethereumDerivationPath
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BackupMnemonicState.Empty)

    val chainAccountImportType = liveData {
        payload.chainAccountData?.chainId?.let {
            emit(interactor.getChain(it).importAccountType)
        }
    }

    private val substrateDerivationPathRegex = Regex("(//?[^/]+)*(///[^/]+)?")

    private val _showInfoEvent = MutableLiveData<Event<Unit>>()
    val showInfoEvent: LiveData<Event<Unit>> = _showInfoEvent

    private val _showInvalidSubstrateDerivationPathError = MutableLiveData<Event<Unit>>()
    val showInvalidSubstrateDerivationPathError: LiveData<Event<Unit>> = _showInvalidSubstrateDerivationPathError

    fun homeButtonClicked() {
        router.backToCreateAccountScreen()
    }

    fun infoClicked() {
        _showInfoEvent.value = Event(Unit)
    }

    override fun onNextClick(
        launcher: ActivityResultLauncher<Intent>
    ) {
        viewModelScope.launch {
            val substrateDerivationPath = substrateDerivationPath.value
            val ethereumDerivationPath = ethereumDerivationPath.value

            if (isFromGoogleBackup) {
                backupPhraseInGoogle(substrateDerivationPath, ethereumDerivationPath, launcher)
                return@launch
            }

            openConfirmMnemonicOnCreate(substrateDerivationPath, ethereumDerivationPath)
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
            if (isFromGoogleBackup) {
                backupPhraseInGoogle(substrateDerivationPath, ethereumDerivationPath, launcher)
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

        val isSubstrateDerivationPathValid = substrateDerivationPath.matches(substrateDerivationPathRegex)
        if (isSubstrateDerivationPathValid.not()) {
            _showInvalidSubstrateDerivationPathError.value = Event(Unit)
            showError(NotValidDerivationPath(resourceManager))
            return
        }

        val createExtras = when (payload.chainAccountData) {
            null -> CreateExtras(
                payload.accountName,
                cryptoTypeModel.cryptoType,
                substrateDerivationPath,
                ethereumDerivationPath
            )

            else -> ConfirmMnemonicPayload.CreateChainExtras(
                payload.accountName,
                cryptoTypeModel.cryptoType,
                substrateDerivationPath,
                ethereumDerivationPath,
                payload.chainAccountData.chainId,
                payload.chainAccountData.metaId
            )
        }
        val payload = ConfirmMnemonicPayload(
            mnemonic,
            createExtras
        )

        router.openConfirmMnemonicOnCreate(payload)
    }

    fun onGoogleBackupClick(
        substrateDerivationPath: String,
        ethereumDerivationPath: String,
        launcher: ActivityResultLauncher<Intent>
    ) {
        viewModelScope.launch {
            backupPhraseInGoogle(substrateDerivationPath, ethereumDerivationPath, launcher)
        }
    }

    private suspend fun backupPhraseInGoogle(
        substrateDerivationPath: String,
        ethereumDerivationPath: String,
        launcher: ActivityResultLauncher<Intent>
    ) {
        val isSubstrateDerivationPathValid = substrateDerivationPath.matches(substrateDerivationPathRegex)
        if (isSubstrateDerivationPathValid.not()) {
            _showInvalidSubstrateDerivationPathError.value = Event(Unit)
            showError(NotValidDerivationPath(resourceManager))
            return
        }

        val isAuthorized = backupService.authorize(launcher)
        if (isAuthorized) {
            openCreateBackupPasswordDialog(
                substrateDerivationPath,
                ethereumDerivationPath
            )
        }
    }

    fun onGoogleSignInSuccess(
        substrateDerivationPath: String,
        ethereumDerivationPath: String
    ) {
        openCreateBackupPasswordDialog(
            substrateDerivationPath,
            ethereumDerivationPath
        )
    }

    override fun onGoogleSignInSuccess() {
        openCreateBackupPasswordDialog(
            substrateDerivationPath.value,
            ethereumDerivationPath.value
        )
    }

    private fun openCreateBackupPasswordDialog(
        substrateDerivationPath: String,
        ethereumDerivationPath: String
    ) {
        val cryptoTypeModel = selectedEncryptionTypeLiveData.value ?: return
        val mnemonicWords = mnemonic.value
        val mnemonic = mnemonicWords
            .map(MnemonicWordModel::word)
            .joinToString(separator = " ")

        router.openCreateBackupPasswordDialog(
            payload = CreateBackupPasswordPayload(
                mnemonic = mnemonic,
                accountName = payload.accountName,
                cryptoType = cryptoTypeModel.cryptoType,
                substrateDerivationPath = substrateDerivationPath,
                ethereumDerivationPath = ethereumDerivationPath
            )
        )
    }

    override fun onGoogleLoginError() {
        // TODO: Implement onGoogleLoginError
    }

    private suspend fun generateMnemonic(): List<MnemonicWordModel> {
        val mnemonic = interactor.generateMnemonic()

        return withContext(Dispatchers.Default) {
            mapMnemonicToMnemonicWords(mnemonic)
        }
    }

    override fun onBackClick() {
        router.back()
    }
}
