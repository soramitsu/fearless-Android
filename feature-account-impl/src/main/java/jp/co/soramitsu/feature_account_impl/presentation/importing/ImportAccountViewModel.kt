package jp.co.soramitsu.feature_account_impl.presentation.importing

import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.switchMap
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.FileRequester
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportError
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.JsonImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.MnemonicImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.RawSeedImportSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ImportAccountViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val resourceManager: ResourceManager,
    private val cryptoTypeChooserMixin: CryptoTypeChooserMixin,
    private val clipboardManager: ClipboardManager,
    private val fileReader: FileReader,
    initialBlockchainType: ImportAccountType
) : BaseViewModel(),
    CryptoTypeChooserMixin by cryptoTypeChooserMixin {

    private val _showEthAccountsDialog = MutableLiveData<Event<Unit>>()
    val showEthAccountsDialog: LiveData<Event<Unit>> = _showEthAccountsDialog

    private val _blockchainTypeFlow = MutableStateFlow(initialBlockchainType)
    val blockchainTypeFlow: Flow<ImportAccountType> = _blockchainTypeFlow

    val nameLiveData = MutableLiveData<String>()

    private val _showSourceChooserLiveData = MutableLiveData<Event<Payload<ImportSource>>>()
    val showSourceSelectorChooserLiveData: LiveData<Event<Payload<ImportSource>>> = _showSourceChooserLiveData

    val substrateDerivationPathLiveData = MutableLiveData<String>()
    val ethereumDerivationPathLiveData = MutableLiveData<String>()

    val sourceTypes = provideSourceType()
    private val _selectedSourceTypeFlow = MutableStateFlow(sourceTypes.first())
    val selectedSourceTypeFlow: Flow<ImportSource> = _selectedSourceTypeFlow

    private val sourceTypeValid = _selectedSourceTypeFlow.asLiveData().switchMap(ImportSource::validationLiveData)

    private val importInProgressLiveData = MutableLiveData(false)

    private val nextButtonEnabledLiveData = sourceTypeValid.combine(nameLiveData) { sourceTypeValid, name ->
        sourceTypeValid && name.isNotEmpty()
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

    init {
        _selectedSourceTypeFlow.value = sourceTypes.first()
    }

    fun homeButtonClicked() {
        router.backToWelcomeScreen()
    }

    fun openSourceChooserClicked() {
        _selectedSourceTypeFlow.value.let {
            _showSourceChooserLiveData.value = Event(Payload(sourceTypes, it))
        }
    }

    fun sourceTypeChanged(it: ImportSource) {
        _selectedSourceTypeFlow.value = it
    }

    fun nextClicked() {
        val source = _selectedSourceTypeFlow.value
        when {
            source is MnemonicImportSource -> {
                import(withEth = true)
            }
            source is RawSeedImportSource && _blockchainTypeFlow.value == ImportAccountType.Substrate -> {
                source.rawSeedLiveData.value?.let {
                    substrateSeed = it
                    _showEthAccountsDialog.value = Event(Unit)
                }
            }
            source is RawSeedImportSource && _blockchainTypeFlow.value == ImportAccountType.Ethereum -> {
                source.rawSeedLiveData.value?.let {
                    ethSeed = it
                    import(withEth = true)
                }
            }
            source is JsonImportSource && _blockchainTypeFlow.value == ImportAccountType.Substrate -> {
                source.jsonContentLiveData.value?.let {
                    substrateJson = it
                    _showEthAccountsDialog.value = Event(Unit)
                }
            }
            source is JsonImportSource && _blockchainTypeFlow.value == ImportAccountType.Ethereum -> {
                source.jsonContentLiveData.value?.let {
                    ethJson = it
                    import(withEth = true)
                }
            }
        }
    }

    private fun import(withEth: Boolean) {
        importInProgressLiveData.value = true
        val sourceType = _selectedSourceTypeFlow.value

        val cryptoType = selectedEncryptionTypeLiveData.value!!.cryptoType
        val substrateDerivationPath = substrateDerivationPathLiveData.value.orEmpty()
        val ethereumDerivationPath = ethereumDerivationPathLiveData.value.orEmpty()
        val name = nameLiveData.value!!

        viewModelScope.launch {
            val result = import(sourceType, name, substrateDerivationPath, ethereumDerivationPath, cryptoType, withEth)

            if (result.isSuccess) {
                continueBasedOnCodeStatus()
            } else {
                handleCreateAccountError(result.requireException())
            }

            importInProgressLiveData.value = false
        }
    }

    fun systemCallResultReceived(requestCode: Int, intent: Intent) {
        val selectedSource = _selectedSourceTypeFlow.value

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
        var errorMessage = _selectedSourceTypeFlow.value.handleError(throwable)

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
            JsonImportSource(
                nameLiveData,
                cryptoTypeChooserMixin.selectedEncryptionTypeLiveData,
                interactor,
                resourceManager,
                clipboardManager,
                fileReader,
                viewModelScope
            ),
            RawSeedImportSource()
        )
    }

    private suspend fun import(
        sourceType: ImportSource,
        name: String,
        substrateDerivationPath: String,
        ethereumDerivationPath: String,
        cryptoType: CryptoType,
        withEth: Boolean
    ): Result<Unit> {
        return when (sourceType) {
            is MnemonicImportSource -> interactor.importFromMnemonic(
                sourceType.mnemonicContentLiveData.value!!,
                name,
                substrateDerivationPath,
                ethereumDerivationPath,
                cryptoType,
                withEth
            )
            is RawSeedImportSource -> interactor.importFromSeed(
                substrateSeed!!,
                name,
                substrateDerivationPath,
                cryptoType,
                ethSeed
            )
            is JsonImportSource -> interactor.importFromJson(
                substrateJson!!,
                sourceType.passwordLiveData.value!!,
                name,
                ethJson
            )
        }
    }

    fun onAddEthAccountConfirmed() {
        _blockchainTypeFlow.value = ImportAccountType.Ethereum
        (_selectedSourceTypeFlow.value as? RawSeedImportSource)?.rawSeedLiveData?.value = ""
        (_selectedSourceTypeFlow.value as? JsonImportSource)?.apply {
            jsonContentLiveData.value = ""
            passwordLiveData.value = ""
        }
    }

    fun onAddEthAccountDeclined() {
        import(false)
    }
}
