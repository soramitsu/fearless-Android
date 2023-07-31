package jp.co.soramitsu.account.impl.presentation.importing

import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.ImportMode.Google
import jp.co.soramitsu.account.api.domain.model.ImportMode.Json
import jp.co.soramitsu.account.api.domain.model.ImportMode.MnemonicPhrase
import jp.co.soramitsu.account.api.domain.model.ImportMode.RawSeed
import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
import jp.co.soramitsu.account.api.presentation.importing.importAccountType
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.account.impl.presentation.importing.ImportAccountFragment.Companion.BLOCKCHAIN_TYPE_KEY
import jp.co.soramitsu.account.impl.presentation.importing.ImportAccountFragment.Companion.IMPORT_MODE_KEY
import jp.co.soramitsu.account.impl.presentation.importing.ImportAccountFragment.Companion.PAYLOAD_KEY
import jp.co.soramitsu.account.impl.presentation.importing.source.model.FileRequester
import jp.co.soramitsu.account.impl.presentation.importing.source.model.ImportError
import jp.co.soramitsu.account.impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.JsonImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.MnemonicImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.RawSeedImportSource
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.switchMap
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.launch

@HiltViewModel
class ImportAccountViewModel @Inject constructor(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val resourceManager: ResourceManager,
    private val cryptoTypeChooserMixin: CryptoTypeChooserMixin,
    private val clipboardManager: ClipboardManager,
    private val fileReader: FileReader,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(),
    CryptoTypeChooserMixin by cryptoTypeChooserMixin {

    private val initialBlockchainType = savedStateHandle.getLiveData<Int>(BLOCKCHAIN_TYPE_KEY)
    private val initialImportMode = savedStateHandle.get(IMPORT_MODE_KEY) ?: MnemonicPhrase
    private val chainCreateAccountData = savedStateHandle.getLiveData<ChainAccountCreatePayload>(PAYLOAD_KEY)

    val isChainAccount = chainCreateAccountData.value != null

    private val _showEthAccountsDialog = MutableLiveData<Event<Unit>>()
    val showEthAccountsDialog: LiveData<Event<Unit>> = _showEthAccountsDialog

    private val _blockchainTypeLiveData = MutableLiveData<ImportAccountType>()
    val blockchainLiveData: LiveData<ImportAccountType> = _blockchainTypeLiveData

    val nameLiveData = MutableLiveData<String>().apply {
        if (isChainAccount) {
            value = ""
        }
    }

    private val _showSourceChooserLiveData = MutableLiveData<Event<Payload<ImportSource>>>()
    val showSourceSelectorChooserLiveData: LiveData<Event<Payload<ImportSource>>> = _showSourceChooserLiveData

    val substrateDerivationPathLiveData = MutableLiveData<String>()
    val ethereumDerivationPathLiveData = MutableLiveData<String>()

    private val _showInvalidSubstrateDerivationPathError = MutableLiveData<Event<Unit>>()
    val showInvalidSubstrateDerivationPathError: LiveData<Event<Unit>> = _showInvalidSubstrateDerivationPathError

    private val substrateDerivationPathRegex = Regex("(//?[^/]+)*(///[^/]+)?")

    val sourceTypes = provideSourceType()
    val initialSelectedSourceType: ImportSource
        get() {
            return when (initialImportMode) {
                MnemonicPhrase -> sourceTypes.firstOrNull { it is MnemonicImportSource }
                RawSeed -> sourceTypes.firstOrNull { it is RawSeedImportSource }
                Json -> sourceTypes.firstOrNull { it is JsonImportSource }
                Google -> null
            } ?: sourceTypes.first()
        }
    private val _selectedSourceTypeLiveData = MutableLiveData(initialSelectedSourceType)
    val selectedSourceLiveData: LiveData<ImportSource> = _selectedSourceTypeLiveData

    private val sourceTypeValid = _selectedSourceTypeLiveData.switchMap(ImportSource::validationLiveData)

    private val importInProgressLiveData = MutableLiveData(false)

    private val nextButtonEnabledLiveData = sourceTypeValid.combine(nameLiveData) { sourceTypeValid, name ->
        sourceTypeValid && (name.isNotEmpty() || isChainAccount)
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
        when {
            chainCreateAccountData.value != null -> launch {
                val importAccountType = interactor.getChain(chainCreateAccountData.value!!.chainId).importAccountType
                _blockchainTypeLiveData.value = importAccountType
            }
            initialBlockchainType.value != null -> _blockchainTypeLiveData.value = initialBlockchainType.value?.let { int ->
                ImportAccountType.values().getOrNull(int)
            }!!
        }
    }

    fun homeButtonClicked() {
        router.backToWelcomeScreen()
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
        val isSubstrateDerivationPathValid = substrateDerivationPathLiveData.value?.matches(substrateDerivationPathRegex)
        if (isSubstrateDerivationPathValid == false) {
            _showInvalidSubstrateDerivationPathError.value = Event(Unit)
            return
        }
        val source = _selectedSourceTypeLiveData.value
        when {
            source is MnemonicImportSource -> {
                import(withEth = true)
            }
            source is RawSeedImportSource && _blockchainTypeLiveData.value == ImportAccountType.Substrate -> {
                source.rawSeedLiveData.value?.let {
                    substrateSeed = it
                    _showEthAccountsDialog.value = Event(Unit)
                }
            }
            source is RawSeedImportSource && _blockchainTypeLiveData.value == ImportAccountType.Ethereum -> {
                source.rawSeedLiveData.value?.let {
                    ethSeed = it
                    import(withEth = true)
                }
            }
            source is JsonImportSource && _blockchainTypeLiveData.value == ImportAccountType.Substrate -> {
                source.jsonContentLiveData.value?.let {
                    substrateJson = it
                    _showEthAccountsDialog.value = Event(Unit)
                }
            }
            source is JsonImportSource && _blockchainTypeLiveData.value == ImportAccountType.Ethereum -> {
                source.jsonContentLiveData.value?.let {
                    ethJson = it
                    import(withEth = true)
                }
            }
        }
    }

    private fun import(withEth: Boolean) {
        val sourceType = _selectedSourceTypeLiveData.value ?: return
        importInProgressLiveData.value = true

        val cryptoType = selectedEncryptionTypeLiveData.value!!.cryptoType
        val substrateDerivationPath = substrateDerivationPathLiveData.value.orEmpty()
        val ethereumDerivationPath = ethereumDerivationPathLiveData.value.orEmpty()
        val name = if (isChainAccount) "" else nameLiveData.value!!

        viewModelScope.launch {
            val result = when (val chainCreateAccountData = chainCreateAccountData.value) {
                null -> import(sourceType, name, substrateDerivationPath, ethereumDerivationPath, cryptoType, withEth)
                else -> importForChain(
                    sourceType,
                    name,
                    substrateDerivationPath,
                    ethereumDerivationPath,
                    cryptoType,
                    chainCreateAccountData
                )
            }
            if (result.isSuccess) {
                continueBasedOnCodeStatus()
            } else {
                handleCreateAccountError(result.requireException())
            }

            importInProgressLiveData.value = false
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
                viewModelScope
            )
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
                withEth,
                isBackedUp = true,
                googleBackupAddress = null
            )
            is RawSeedImportSource -> interactor.importFromSeed(
                substrateSeed!!,
                name,
                substrateDerivationPath,
                cryptoType,
                ethSeed,
                googleBackupAddress = null
            )
            is JsonImportSource -> interactor.importFromJson(
                substrateJson!!,
                sourceType.passwordLiveData.value!!,
                name,
                ethJson,
                googleBackupAddress = null
            )
        }
    }

    private suspend fun importForChain(
        sourceType: ImportSource,
        name: String,
        substrateDerivationPath: String,
        ethereumDerivationPath: String,
        cryptoType: CryptoType,
        chainCreateAccountData: ChainAccountCreatePayload
    ): Result<Unit> {
        return when (sourceType) {
            is MnemonicImportSource -> {
                sourceType.mnemonicContentLiveData.value?.let { mnemonicWords ->
                    interactor.importChainAccountFromMnemonic(
                        metaId = chainCreateAccountData.metaId,
                        chainId = chainCreateAccountData.chainId,
                        accountName = name,
                        mnemonicWords = mnemonicWords,
                        cryptoType = cryptoType,
                        substrateDerivationPath = substrateDerivationPath,
                        ethereumDerivationPath = ethereumDerivationPath
                    )
                } ?: Result.failure(IllegalArgumentException("Mnemonic not specified"))
            }
            is RawSeedImportSource -> {
                when (interactor.getChain(chainCreateAccountData.chainId).isEthereumBased) {
                    true -> ethSeed
                    else -> substrateSeed
                }?.let { seed ->
                    interactor.importChainFromSeed(
                        metaId = chainCreateAccountData.metaId,
                        chainId = chainCreateAccountData.chainId,
                        accountName = name,
                        seed = seed,
                        substrateDerivationPath = substrateDerivationPath,
                        selectedEncryptionType = cryptoType
                    )
                } ?: Result.failure(IllegalArgumentException("Seed not specified"))
            }
            is JsonImportSource -> {
                when (interactor.getChain(chainCreateAccountData.chainId).isEthereumBased) {
                    true -> ethJson
                    else -> substrateJson
                }?.let { json ->
                    interactor.importChainFromJson(
                        metaId = chainCreateAccountData.metaId,
                        chainId = chainCreateAccountData.chainId,
                        accountName = name,
                        json = json,
                        password = sourceType.passwordLiveData.value!!
                    )
                } ?: Result.failure(IllegalArgumentException("Json not specified"))
            }
        }
    }

    fun onAddEthAccountConfirmed() {
        _blockchainTypeLiveData.value = ImportAccountType.Ethereum
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
