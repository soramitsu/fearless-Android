package jp.co.soramitsu.account.impl.presentation.importing.source.model

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.ImportJsonData
import jp.co.soramitsu.account.api.presentation.accountSource.AccountSource
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
import jp.co.soramitsu.account.impl.data.mappers.mapCryptoTypeToCryptoTypeModel
import jp.co.soramitsu.account.impl.presentation.importing.FileReader
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.isNotEmpty
import jp.co.soramitsu.common.utils.sendEvent
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.shared_utils.encrypt.json.JsonSeedDecodingException.IncorrectPasswordException
import jp.co.soramitsu.shared_utils.encrypt.json.JsonSeedDecodingException.InvalidJsonException
import jp.co.soramitsu.shared_utils.exceptions.Bip39Exception
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.bouncycastle.util.encoders.DecoderException

class WrongBlockchainImportJsonException : Exception()

class ImportError(
    @StringRes val titleRes: Int = R.string.common_error_general_title,
    @StringRes val messageRes: Int = R.string.common_undefined_error_message
)

sealed class ImportSource(@StringRes nameRes: Int, @StringRes hintRes: Int, @DrawableRes iconRes: Int) : AccountSource(nameRes, hintRes, iconRes, false) {

    private val _validationLiveData = MediatorLiveData<Boolean>()
    val validationLiveData: LiveData<Boolean> = _validationLiveData

    init {
        _validationLiveData.value = false
    }

    abstract fun isFieldsValid(): Boolean

    open fun handleError(throwable: Throwable): ImportError? = null

    protected fun addValidationSource(liveData: LiveData<*>) {
        _validationLiveData.addSource(liveData) {
            _validationLiveData.value = isFieldsValid()
        }
    }
}

private const val PICK_FILE_RESULT_CODE = 101

class JsonImportSource(
    private val nameLiveData: MutableLiveData<String>,
    private val cryptoTypeLiveData: MutableLiveData<CryptoTypeModel>,
    private val interactor: AccountInteractor,
    private val resourceManager: ResourceManager,
    private val clipboardManager: ClipboardManager,
    private val fileReader: FileReader,
    private val scope: CoroutineScope
) : ImportSource(R.string.recovery_json, R.string.recover_json_hint, R.drawable.ic_save_type_json), FileRequester {

    val blockchainTypeFlow = MutableStateFlow<ImportAccountType?>(null)
    val jsonContentLiveData = MutableLiveData<String>()
    val passwordLiveData = MutableLiveData<String>()

    private val _showJsonInputOptionsEvent = MutableLiveData<Event<Unit>>()
    val showJsonInputOptionsEvent: LiveData<Event<Unit>> = _showJsonInputOptionsEvent
    private val _showImportErrorEvent = MutableLiveData<Event<ImportError>>()
    val showImportErrorEvent: LiveData<Event<ImportError>> = _showImportErrorEvent

    override val chooseJsonFileEvent = MutableLiveData<Event<RequestCode>>()

    init {
        addValidationSource(jsonContentLiveData)

        addValidationSource(passwordLiveData)
    }

    override fun isFieldsValid(): Boolean {
        return jsonContentLiveData.isNotEmpty() && passwordLiveData.isNotEmpty()
    }

    override fun handleError(throwable: Throwable): ImportError? {
        return when (throwable) {
            is WrongBlockchainImportJsonException -> ImportError(
                titleRes = R.string.import_json_invalid_format_title,
                messageRes = R.string.import_json_invalid_import_type_message
            )

            is IncorrectPasswordException -> ImportError(
                titleRes = R.string.import_json_invalid_password_title,
                messageRes = R.string.import_json_invalid_password
            )

            is InvalidJsonException -> ImportError(
                titleRes = R.string.import_json_invalid_format_title,
                messageRes = R.string.import_json_invalid_format_message
            )

            else -> null
        }
    }

    override fun fileChosen(uri: Uri) {
        scope.launch {
            val content = fileReader.readFile(uri)!!

            jsonReceived(content)
        }
    }

    fun jsonClicked() {
        _showJsonInputOptionsEvent.sendEvent()
    }

    fun chooseFileClicked() {
        chooseJsonFileEvent.value = Event(PICK_FILE_RESULT_CODE)
    }

    fun pasteClicked() {
        clipboardManager.getFromClipboard()?.let(this::jsonReceived)
    }

    private fun jsonReceived(newJson: String) {
        scope.launch {
            val result = interactor.processAccountJson(newJson)

            runCatching {
                val jsonResult = result.getOrThrow()
                val parsedJsonEncryptionType = jsonResult.encryptionType
                val importBlockchainType = blockchainTypeFlow.value

                checkJsonForBlockchainType(importBlockchainType, parsedJsonEncryptionType)

                jsonContentLiveData.value = newJson

                handleParsedImportData(jsonResult)
            }.onFailure {
                handleError(it)?.let { importError ->
                    _showImportErrorEvent.value = Event(importError)
                }
            }
        }
    }

    @Throws(WrongBlockchainImportJsonException::class, InvalidJsonException::class)
    private fun checkJsonForBlockchainType(importBlockchainType: ImportAccountType?, parsedJsonEncryptionType: CryptoType?) {
        importBlockchainType ?: return
        parsedJsonEncryptionType ?: return

        when (importBlockchainType) {
            ImportAccountType.Substrate -> when (parsedJsonEncryptionType) {
                CryptoType.SR25519,
                CryptoType.ED25519 -> return

                CryptoType.ECDSA -> throw WrongBlockchainImportJsonException()
            }

            ImportAccountType.Ethereum -> when (parsedJsonEncryptionType) {
                CryptoType.SR25519,
                CryptoType.ED25519 -> throw InvalidJsonException()

                CryptoType.ECDSA -> return
            }
        }
    }

    private fun handleParsedImportData(importJsonData: ImportJsonData) {
        val cryptoModel = mapCryptoTypeToCryptoTypeModel(resourceManager, importJsonData.encryptionType)
        cryptoTypeLiveData.value = cryptoModel

        importJsonData.name?.let { nameLiveData.value = it }
    }
}

class MnemonicImportSource : ImportSource(R.string.recovery_mnemonic, R.string.recovery_mnemonic_hint, R.drawable.ic_save_type_mnemonic) {

    val mnemonicContentLiveData = MutableLiveData<String>()

    override fun isFieldsValid() = mnemonicContentLiveData.isNotEmpty()

    init {
        addValidationSource(mnemonicContentLiveData)
    }

    override fun handleError(throwable: Throwable): ImportError? {
        return when (throwable) {
            is Bip39Exception -> ImportError(
                titleRes = R.string.import_mnemonic_invalid_title,
                messageRes = R.string.mnemonic_error_try_another_one
            )
            else -> null
        }
    }
}

class RawSeedImportSource : ImportSource(R.string.recovery_raw_seed, R.string.recovery_raw_seed_hint, R.drawable.ic_save_type_seed) {

    val rawSeedLiveData = MutableLiveData<String>()

    override fun isFieldsValid() = rawSeedLiveData.isNotEmpty()

    override fun handleError(throwable: Throwable): ImportError? {
        return when (throwable) {
            is IllegalArgumentException, is DecoderException -> ImportError(
                titleRes = R.string.import_seed_invalid_title,
                messageRes = R.string.account_import_invalid_seed
            )
            else -> null
        }
    }

    init {
        addValidationSource(rawSeedLiveData)
    }
}
