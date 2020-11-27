package jp.co.soramitsu.feature_account_impl.presentation.importing.source.model

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.DEFAULT_ERROR_HANDLER
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.isNotEmpty
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.sendEvent
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecodingException.InvalidJsonException
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecodingException.IncorrectPasswordException
import jp.co.soramitsu.fearless_utils.exceptions.Bip39Exception
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.ImportJsonData
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.common.accountSource.AccountSource
import jp.co.soramitsu.feature_account_impl.data.mappers.mapCryptoTypeToCryptoTypeModel
import jp.co.soramitsu.feature_account_impl.data.mappers.mapNetworkTypeToNetworkModel
import jp.co.soramitsu.feature_account_impl.presentation.importing.FileReader
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import org.bouncycastle.util.encoders.DecoderException

class ImportError(
    @StringRes val titleRes: Int = R.string.common_error_general_title,
    @StringRes val messageRes: Int = R.string.common_undefined_error_message
)

sealed class ImportSource(@StringRes nameRes: Int) : AccountSource(nameRes) {

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
    private val networkLiveData: MutableLiveData<NetworkModel>,
    private val nameLiveData: MutableLiveData<String>,
    private val cryptoTypeLiveData: MutableLiveData<CryptoTypeModel>,
    private val interactor: AccountInteractor,
    private val resourceManager: ResourceManager,
    private val clipboardManager: ClipboardManager,
    private val fileReader: FileReader,
    private val disposables: CompositeDisposable
) : ImportSource(R.string.recovery_json), FileRequester {

    val jsonContentLiveData = MutableLiveData<String>()
    val passwordLiveData = MutableLiveData<String>()

    private val _showJsonInputOptionsEvent = MutableLiveData<Event<Unit>>()
    val showJsonInputOptionsEvent: LiveData<Event<Unit>> = _showJsonInputOptionsEvent

    private val _enableNetworkInputLiveData = MutableLiveData<Boolean>(false)
    val enableNetworkInputLiveData = _enableNetworkInputLiveData

    val showNetworkWarningLiveData = enableNetworkInputLiveData

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
        disposables += fileReader.readFile(uri)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(::jsonReceived)
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
        jsonContentLiveData.value = newJson

        disposables += interactor.processAccountJson(newJson)
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(::handleParsedImportData, DEFAULT_ERROR_HANDLER)
    }

    private fun handleParsedImportData(importJsonData: ImportJsonData) {
        _enableNetworkInputLiveData.value = importJsonData.networkType == null

        importJsonData.networkType?.let {
            val networkModel = mapNetworkTypeToNetworkModel(it)
            networkLiveData.value = networkModel
        }

        val cryptoModel = mapCryptoTypeToCryptoTypeModel(resourceManager, importJsonData.encryptionType)
        cryptoTypeLiveData.value = cryptoModel

        nameLiveData.value = importJsonData.name
    }
}

class MnemonicImportSource : ImportSource(R.string.recovery_passphrase) {

    val mnemonicContentLiveData = MutableLiveData<String>()

    override fun isFieldsValid() = mnemonicContentLiveData.isNotEmpty()

    init {
        addValidationSource(mnemonicContentLiveData)
    }

    override fun handleError(throwable: Throwable): ImportError? {
        return when (throwable) {
            is Bip39Exception -> ImportError(
                titleRes = R.string.import_mnemonic_invalid_title,
                messageRes = R.string.error_try_another_one
            )
            else -> null
        }
    }
}

class RawSeedImportSource : ImportSource(R.string.recovery_raw_seed) {

    val rawSeedLiveData = MutableLiveData<String>()

    override fun isFieldsValid() = rawSeedLiveData.isNotEmpty()

    override fun handleError(throwable: Throwable): ImportError? {
        return when (throwable) {
            is IllegalArgumentException, is DecoderException -> ImportError(
                titleRes = R.string.import_seed_invalid_title,
                messageRes = R.string.import_seed_invalid_message
            )
            else -> null
        }
    }

    init {
        addValidationSource(rawSeedLiveData)
    }
}