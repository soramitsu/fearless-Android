package jp.co.soramitsu.account.impl.presentation.create_backup_password

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.presentation.create_backup_password.CreateBackupPasswordPayload
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.backup.BackupService
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
import jp.co.soramitsu.runtime.multiNetwork.chain.model.westendChainId
import jp.co.soramitsu.shared_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.shared_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.shared_utils.extensions.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val heightDiffDpFlow = MutableStateFlow(0.dp)

    private val originPassword = MutableStateFlow("")
    private val confirmPassword = MutableStateFlow("")
    private val isUserAgreedWithStatements = MutableStateFlow(false)
    private val isAgreementsChecked = MutableStateFlow(false)
    private val isLoading = MutableStateFlow(false)
    private val isOriginPasswordVisible = MutableStateFlow(false)
    private val isConfirmPasswordVisible = MutableStateFlow(false)
    private val passwordMatchingTextResource = combine(originPassword, confirmPassword) { originPassword, confirmPassword ->
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
    private val arePasswordsMatched = combine(originPassword, confirmPassword) { originPassword, confirmPassword ->
        originPassword.isNotEmpty() &&
            confirmPassword.length >= originPassword.length &&
            confirmPassword == originPassword
    }

    private val highlightConfirmPassword = combine(originPassword, confirmPassword) { originPassword, confirmPassword ->
        originPassword.isNotEmpty() &&
            confirmPassword.length >= originPassword.length &&
            confirmPassword != originPassword
    }
    private val isSetButtonEnabled = combine(arePasswordsMatched, isAgreementsChecked) { arePasswordsMatched, isAgreementsChecked ->
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
        isLoading = false,
        heightDiffDp = 0.dp
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
        isConfirmPasswordVisible,
        heightDiffDpFlow
    ) { originPassword, confirmPassword, isUserAgreedWithStatements, isAgreementsChecked, passwordMatchingTextResource,
        highlightConfirmPassword, isSetButtonEnabled, isLoading, isOriginPasswordVisible, isConfirmPasswordVisible, heightDiffDp ->
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
            isLoading = isLoading,
            heightDiffDp = heightDiffDp
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
                val walletId = if (payload.createAccount) {
                    // only mnemonic creation in this flow
                    importFromMnemonic()
                } else {
                    payload.walletId ?: error("Wallet id not specified")
                }

                backupAccountToGoogle(walletId)
            }
                .onSuccess {
                    val walletId = payload.walletId ?: interactor.selectedMetaAccount().id
                    interactor.updateWalletBackedUp(walletId)
                    continueBasedOnCodeStatus()
                    isLoading.value = false
                }
                .onFailure {
                    isLoading.value = false
                    handleAccountBackupError(it)
                }
        }
    }

    private fun handleAccountBackupError(it: Throwable) {
        showError(it)
    }

    private suspend fun importFromMnemonic(): Long {
        val mnemonic = payload.mnemonic ?: error("No mnemonic specified")
        return interactor.importFromMnemonic(
            walletName = payload.accountName,
            mnemonic = mnemonic,
            substrateDerivationPath = payload.substrateDerivationPath,
            ethereumDerivationPath = payload.ethereumDerivationPath,
            selectedEncryptionType = payload.cryptoType,
            withEth = true,
            isBackedUp = false,
            googleBackupAddress = null
        ).getOrThrow()
    }

    private suspend fun backupAccountToGoogle(walletId: Long) {
        withContext(Dispatchers.IO) {
            val password = originPassword.value

            val wallet = interactor.getMetaAccount(walletId)
            val westendChain = interactor.getChain(westendChainId)
            val googleBackupAddress = wallet.address(westendChain) ?: error("error obtaining google backup address")

            val jsonResult = interactor.generateRestoreJson(
                metaId = walletId,
                chainId = polkadotChainId,
                password = password
            )
            val substrateJson = jsonResult.getOrNull()
            val ethJsonResult = interactor.generateRestoreJson(
                metaId = walletId,
                chainId = moonriverChainId,
                password = password
            )
            val ethJson = ethJsonResult.getOrNull()

            val metaAccountSecrets = interactor.getMetaAccountSecrets(walletId)

            val substrateDerivationPath = metaAccountSecrets?.get(MetaAccountSecrets.SubstrateDerivationPath).orEmpty()
            val ethereumDerivationPath = metaAccountSecrets?.get(MetaAccountSecrets.EthereumDerivationPath).orEmpty()
            val entropy = metaAccountSecrets?.get(MetaAccountSecrets.Entropy)?.clone()
            val mnemonic = entropy?.let { MnemonicCreator.fromEntropy(it).words }
            val substrateSeed = (
                    metaAccountSecrets?.get(MetaAccountSecrets.Seed) ?: mnemonic?.let {
                        seedFromMnemonic(
                            mnemonic,
                            substrateDerivationPath.nullIfEmpty()
                        )
                    }
                    )?.toHexString(withPrefix = true)
            val ethSeed = metaAccountSecrets?.get(MetaAccountSecrets.EthereumKeypair)?.get(KeyPairSchema.PrivateKey)?.toHexString(withPrefix = true)

            val backupAccountTypes = interactor.getSupportedBackupTypes(walletId).toList()

            backupService.saveBackupAccount(
                account = DecryptedBackupAccount(
                    name = payload.accountName,
                    address = googleBackupAddress,
                    mnemonicPhrase = mnemonic,
                    substrateDerivationPath = substrateDerivationPath,
                    ethDerivationPath = ethereumDerivationPath,
                    cryptoType = payload.cryptoType,
                    backupAccountType = backupAccountTypes,
                    seed = Seed(substrateSeed = substrateSeed, ethSeed),
                    json = Json(substrateJson = substrateJson, ethJson)
                ),
                password = password
            )
        }
    }

    private fun seedFromMnemonic(mnemonic: String, derivationPath: String?): ByteArray {
        val password = derivationPath?.let { SubstrateJunctionDecoder.decode(it).password }
        return SubstrateSeedFactory.deriveSeed32(mnemonic, password).seed
    }

    private suspend fun continueBasedOnCodeStatus() {
        if (payload.createAccount) {
            if (interactor.isCodeSet()) {
                accountRouter.openMain()
            } else {
                accountRouter.openCreatePincode()
            }
        } else {
            accountRouter.back()
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

    fun setHeightDiffDp(value: Dp) {
        heightDiffDpFlow.value = value
    }
}
