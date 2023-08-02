package jp.co.soramitsu.account.impl.presentation.create_backup_password

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.presentation.create_backup_password.CreateBackupPasswordPayload
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.backup.domain.models.Json
import jp.co.soramitsu.backup.domain.models.Seed
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.TextSelectableItemState
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.moonriverChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.shared_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.shared_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.scale.EncodableStruct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import jp.co.soramitsu.common.utils.combine as combineFlows

@HiltViewModel
class CreateBackupPasswordViewModel @Inject constructor(
    private val accountRouter: AccountRouter,
    private val backupService: BackupService,
    private val savedStateHandle: SavedStateHandle,
    private val interactor: AccountInteractor,
    private val resourceManager: ResourceManager
) : BaseViewModel(), CreateBackupPasswordCallback {

    private val payload = savedStateHandle.get<CreateBackupPasswordPayload>(CreateBackupPasswordDialog.PAYLOAD_KEY)!!

    private val originPassword = MutableStateFlow("")
    private val confirmPassword = MutableStateFlow("")
    private val isUserAgreedWithStatements = MutableStateFlow(false)
    private val isAgreementsChecked = MutableStateFlow(false)
    private val isLoading = MutableStateFlow(false)
    private val isOriginPasswordVisible = MutableStateFlow(false)
    private val isConfirmPasswordVisible = MutableStateFlow(false)
    private val passwordMatchingTextResource = combine(originPassword, confirmPassword) {
            originPassword, confirmPassword ->
        if (originPassword.isNotEmpty() && confirmPassword.length >= originPassword.length) {
            if (confirmPassword == originPassword) {
                R.string.create_backup_password_matched
            } else {
                R.string.create_backup_password_not_matched
            }
        } else {
            null
        }
    }
    private val arePasswordsMatched = combine(originPassword, confirmPassword) {
            originPassword, confirmPassword ->
        originPassword.isNotEmpty() &&
            confirmPassword.length >= originPassword.length &&
            confirmPassword == originPassword
    }

    private val highlightConfirmPassword = combine(originPassword, confirmPassword) {
            originPassword, confirmPassword ->
        originPassword.isNotEmpty() &&
            confirmPassword.length >= originPassword.length &&
            confirmPassword != originPassword
    }
    private val isSetButtonEnabled = combine(arePasswordsMatched, isAgreementsChecked) {
            arePasswordsMatched, isAgreementsChecked ->
        arePasswordsMatched && isAgreementsChecked
    }

    private val initialState = CreateBackupPasswordViewState(
        originPasswordViewState = createTextInputViewState(
            hint = resourceManager.getString(R.string.export_json_password_new),
            password = originPassword.value,
            isPasswordVisible = false
        ),
        confirmPasswordViewState = createTextInputViewState(
            hint = resourceManager.getString(R.string.export_json_password_confirm),
            password = confirmPassword.value,
            isPasswordVisible = false
        ),
        isUserAgreedWithStatements = isUserAgreedWithStatements.value,
        agreementsState = TextSelectableItemState(
            isSelected = isAgreementsChecked.value,
            textResId = jp.co.soramitsu.common.R.string.create_backup_password_agreements
        ),
        passwordMatchingTextResource = null,
        highlightConfirmPassword = false,
        isSetButtonEnabled = true,
        isLoading = false
    )
    val state = combineFlows(
        originPassword,
        confirmPassword,
        isUserAgreedWithStatements,
        isAgreementsChecked,
        passwordMatchingTextResource,
        highlightConfirmPassword,
        isSetButtonEnabled,
        isLoading,
        isOriginPasswordVisible,
        isConfirmPasswordVisible
    ) {
            originPassword, confirmPassword, isUserAgreedWithStatements,
            isAgreementsChecked, passwordMatchingTextResource,
            highlightConfirmPassword, isSetButtonEnabled, isLoading, isOriginPasswordVisible, isConfirmPasswordVisible ->
        CreateBackupPasswordViewState(
            originPasswordViewState = createTextInputViewState(
                hint = resourceManager.getString(R.string.export_json_password_new),
                password = originPassword,
                isPasswordVisible = isOriginPasswordVisible
            ),
            confirmPasswordViewState = createTextInputViewState(
                hint = resourceManager.getString(R.string.export_json_password_confirm),
                password = confirmPassword,
                isPasswordVisible = isConfirmPasswordVisible
            ),
            isUserAgreedWithStatements = isUserAgreedWithStatements,
            agreementsState = TextSelectableItemState(
                isSelected = isAgreementsChecked,
                textResId = R.string.create_backup_password_agreements
            ),
            passwordMatchingTextResource = passwordMatchingTextResource,
            highlightConfirmPassword = highlightConfirmPassword,
            isSetButtonEnabled = isSetButtonEnabled,
            isLoading = isLoading
        )
    }.stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = initialState)

    private fun createTextInputViewState(
        hint: String,
        password: String,
        isPasswordVisible: Boolean
    ): TextInputViewState {
        return TextInputViewState(
            hint = hint,
            text = password,
            mode = if (isPasswordVisible) TextInputViewState.Mode.Text else TextInputViewState.Mode.Password,
            endIcon = if (isPasswordVisible) R.drawable.ic_eye_enabled else R.drawable.ic_eye_disabled
        )
    }

    override fun onOriginPasswordChange(password: String) {
        originPassword.value = password
    }

    override fun onConfirmPasswordChange(password: String) {
        confirmPassword.value = password
    }

    override fun onIsUserAgreedWithStatementsChange(isChecked: Boolean) {
        isUserAgreedWithStatements.value = isChecked
    }

    override fun onBackClick() {
        accountRouter.back()
    }

    override fun onApplyPasswordClick() {
        isLoading.value = true
        viewModelScope.launch {
            runCatching {
                if (payload.createAccount) {
                    importFromBackup()
                } else {
                    val currentMetaId = interactor.selectedMetaAccount().id
                    interactor.updateWalletBackedUp(currentMetaId)
                }
                saveBackupAccount()
            }
                .onSuccess {
                    continueBasedOnCodeStatus()
                    isLoading.value = true
                }
                .onFailure {
                    isLoading.value = true
                    handleAccountBackupError(it)
                }
        }
    }

    private fun handleAccountBackupError(it: Throwable) {
        showError(it)
    }

    private suspend fun importFromBackup() {
        val mnemonic = payload.mnemonic ?: error("No mnemonic in importing from backup")
        interactor.importFromMnemonic(
            walletName = payload.accountName,
            mnemonic = mnemonic,
            substrateDerivationPath = payload.substrateDerivationPath,
            ethereumDerivationPath = payload.ethereumDerivationPath,
            selectedEncryptionType = payload.cryptoType,
            withEth = true,
            isBackedUp = true,
            googleBackupAddress = null
        ).getOrThrow()
    }

    private suspend fun saveBackupAccount() {
        val password = originPassword.value
        val address = interactor.getGoogleBackupAddress()

        val metaId = interactor.selectedMetaAccount().id
        val jsonResult = interactor.generateRestoreJson(
            metaId = metaId,
            chainId = polkadotChainId,
            password = password
        )
        val substrateJson = jsonResult.getOrNull()
        val ethJsonResult = interactor.generateRestoreJson(
            metaId = metaId,
            chainId = moonriverChainId,
            password = password
        )
        val ethJson = ethJsonResult.getOrNull()
        val metaAccountSecrets = interactor.getMetaAccountSecrets(metaId)
        val substrateSeed = (metaAccountSecrets?.get(MetaAccountSecrets.Seed) ?: seedFromEntropy(metaAccountSecrets))?.toHexString(withPrefix = true)
        val ethSeed = metaAccountSecrets?.get(MetaAccountSecrets.EthereumKeypair)?.get(KeyPairSchema.PrivateKey)?.toHexString(withPrefix = true)

        backupService.saveBackupAccount(
            account = DecryptedBackupAccount(
                name = payload.accountName,
                address = address,
                mnemonicPhrase = payload.mnemonic,
                substrateDerivationPath = payload.substrateDerivationPath,
                ethDerivationPath = payload.ethereumDerivationPath,
                cryptoType = payload.cryptoType,
                backupAccountType = listOf(BackupAccountType.PASSPHRASE, BackupAccountType.SEED, BackupAccountType.JSON),
                seed = Seed(substrateSeed = substrateSeed, ethSeed),
                json = Json(substrateJson = substrateJson, ethJson)
            ),
            password = password
        )
    }

    private fun seedFromEntropy(secret: EncodableStruct<MetaAccountSecrets>?) = secret?.get(MetaAccountSecrets.Entropy)?.let { entropy ->
        val mnemonicWords = MnemonicCreator.fromEntropy(entropy).words
        val derivationPath = secret[MetaAccountSecrets.SubstrateDerivationPath]?.nullIfEmpty()
        val password = derivationPath?.let { SubstrateJunctionDecoder.decode(it).password }
        SubstrateSeedFactory.deriveSeed32(mnemonicWords, password).seed
    }

    private suspend fun continueBasedOnCodeStatus() {
        if (interactor.isCodeSet()) {
            accountRouter.openMain()
        } else {
            accountRouter.openCreatePincode()
        }
    }

    override fun onAgreementsClick() {
        isAgreementsChecked.value = !isAgreementsChecked.value
    }

    override fun onOriginPasswordVisibilityClick() {
        isOriginPasswordVisible.value = isOriginPasswordVisible.value.not()
    }

    override fun onConfirmPasswordVisibilityClick() {
        isConfirmPasswordVisible.value = isConfirmPasswordVisible.value.not()
    }
}
