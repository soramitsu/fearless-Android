package jp.co.soramitsu.feature_account_impl.presentation.importing.source.model

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.utils.isNotEmpty
import jp.co.soramitsu.fearless_utils.encrypt.JsonSeedDecodingException.IncorrectPasswordException
import jp.co.soramitsu.fearless_utils.encrypt.JsonSeedDecodingException.InvalidJsonException
import jp.co.soramitsu.fearless_utils.exceptions.Bip39Exception
import jp.co.soramitsu.feature_account_impl.R

sealed class ImportSource(@StringRes val nameRes: Int) {

    private val _validationLiveData = MediatorLiveData<Boolean>()
    val validationLiveData = _validationLiveData

    init {
        _validationLiveData.value = false
    }

    abstract fun isFieldsValid(): Boolean

    @StringRes
    open fun handleError(throwable: Throwable): Int? = null

    protected fun addValidationSource(liveData: LiveData<*>) {
        _validationLiveData.addSource(liveData) {
            _validationLiveData.value = isFieldsValid()
        }
    }
}

class JsonImportSource : ImportSource(R.string.recovery_json) {

    val jsonContentLiveData = MutableLiveData<String>()
    val passwordLiveData = MutableLiveData<String>()

    override fun isFieldsValid(): Boolean {
        return jsonContentLiveData.isNotEmpty() && passwordLiveData.isNotEmpty()
    }

    override fun handleError(throwable: Throwable): Int? {
        return when (throwable) {
            is IncorrectPasswordException -> R.string.import_json_invalid_password
            is InvalidJsonException -> R.string.import_json_invalid_format
            else -> null
        }
    }

    init {
        addValidationSource(jsonContentLiveData)

        addValidationSource(passwordLiveData)
    }
}

class MnemonicImportSource : ImportSource(R.string.recovery_passphrase) {

    val mnemonicContentLiveData = MutableLiveData<String>()

    override fun isFieldsValid() = mnemonicContentLiveData.isNotEmpty()

    init {
        addValidationSource(mnemonicContentLiveData)
    }

    override fun handleError(throwable: Throwable): Int? {
        return when (throwable) {
            is Bip39Exception -> R.string.access_restore_phrase_error_message
            else -> null
        }
    }
}

class RawSeedImportSource : ImportSource(R.string.recovery_raw_seed) {

    val rawSeedLiveData = MutableLiveData<String>()

    override fun isFieldsValid() = rawSeedLiveData.isNotEmpty()

    init {
        addValidationSource(rawSeedLiveData)
    }
}