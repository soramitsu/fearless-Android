package jp.co.soramitsu.feature_account_impl.presentation.importing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.fearless_utils.exceptions.Bip39Exception
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.SourceType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.NetworkChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.SourceTypeModel

class ImportAccountViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val resourceManager: ResourceManager,
    private val cryptoTypeChooserMixin: CryptoTypeChooserMixin,
    private val networkChooserMixin: NetworkChooserMixin
) : BaseViewModel(),
    CryptoTypeChooserMixin by cryptoTypeChooserMixin,
    NetworkChooserMixin by networkChooserMixin {

    private val _usernameVisibilityLiveData = MediatorLiveData<Boolean>()
    val usernameVisibilityLiveData: LiveData<Boolean> = _usernameVisibilityLiveData

    private val _passwordVisibilityLiveData = MediatorLiveData<Boolean>()
    val passwordVisibilityLiveData: LiveData<Boolean> = _passwordVisibilityLiveData

    private val _jsonInputVisibilityLiveData = MediatorLiveData<Boolean>()
    val jsonInputVisibilityLiveData: LiveData<Boolean> = _jsonInputVisibilityLiveData

    private val _qrScanStartLiveData = MutableLiveData<Event<Unit>>()
    val qrScanStartLiveData: LiveData<Event<Unit>> = _qrScanStartLiveData

    private val _nextButtonEnabledLiveData = MutableLiveData<Boolean>()
    val nextButtonEnabledLiveData: LiveData<Boolean> = _nextButtonEnabledLiveData

    private val _sourceTypesLiveData = MutableLiveData<List<SourceTypeModel>>()
    val sourceTypesLiveData: LiveData<List<SourceTypeModel>> = _sourceTypesLiveData

    private val _selectedSourceTypeLiveData = MediatorLiveData<SourceTypeModel>()
    val selectedSourceTypeLiveData: LiveData<SourceTypeModel> = _selectedSourceTypeLiveData

    private val _sourceTypeChooserDialogInitialData =
        MutableLiveData<Event<List<SourceTypeModel>>>()
    val sourceTypeChooserDialogInitialData: LiveData<Event<List<SourceTypeModel>>> =
        _sourceTypeChooserDialogInitialData

    init {
        disposables += networkDisposable
        disposables += cryptoDisposable

        _selectedSourceTypeLiveData.addSource(sourceTypesLiveData) {
            val selected = it.firstOrNull { it.isSelected } ?: it.first()
            _selectedSourceTypeLiveData.value = selected
        }

        _usernameVisibilityLiveData.addSource(selectedSourceTypeLiveData) {
            _usernameVisibilityLiveData.value = it.sourceType != SourceType.KEYSTORE
        }

        _passwordVisibilityLiveData.addSource(selectedSourceTypeLiveData) {
            _passwordVisibilityLiveData.value = it.sourceType == SourceType.KEYSTORE
        }

        _jsonInputVisibilityLiveData.addSource(selectedSourceTypeLiveData) {
            _jsonInputVisibilityLiveData.value = it.sourceType == SourceType.KEYSTORE
        }

        disposables.add(
            interactor.getSourceTypesWithSelected()
                .subscribeOn(Schedulers.io())
                .map { mapSourceTypeToSourceTypeMode(it.first, it.second) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _sourceTypesLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun homeButtonClicked() {
        router.backToWelcomeScreen()
    }

    fun sourceTypeInputClicked() {
        sourceTypesLiveData.value?.let {
            _sourceTypeChooserDialogInitialData.value = Event(it)
        }
    }

    fun sourceTypeChanged(it: SourceTypeModel) {
        _selectedSourceTypeLiveData.value = it
    }

    fun qrScanClicked() {
        _qrScanStartLiveData.value = Event(Unit)
    }

    fun nextClicked(
        keyString: String,
        username: String,
        password: String,
        json: String,
        derivationPath: String
    ) {
        val node = selectedNetworkLiveData.value?.defaultNode!!
        val sourceType = selectedSourceTypeLiveData.value!!.sourceType
        val cryptoType = selectedEncryptionTypeLiveData.value!!.cryptoType

        val importDisposable = when (sourceType) {
            SourceType.MNEMONIC_PASSPHRASE -> interactor.importFromMnemonic(
                keyString,
                username,
                derivationPath,
                cryptoType,
                node
            )
            SourceType.RAW_SEED -> interactor.importFromSeed(
                keyString,
                username,
                derivationPath,
                cryptoType,
                node
            )
            SourceType.KEYSTORE -> interactor.importFromJson(
                json,
                password,
                node.networkType
            )
        }

        disposables.add(
            importDisposable
                .andThen(interactor.isCodeSet())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(::continueBasedOnCodeStatus) {
                    if (it is Bip39Exception) {
                        onError(R.string.access_restore_phrase_error_message)
                    } else {
                        onError(R.string.common_undefined_error_message)
                    }
                }
        )
    }

    private fun continueBasedOnCodeStatus(isCodeSet: Boolean) {
        if (isCodeSet) {
            router.openMain()
        } else {
            router.openCreatePincode()
        }
    }

    fun inputChanges(input1: String, input2: String) {
        _nextButtonEnabledLiveData.value = input1.isNotEmpty() && input2.isNotEmpty()
    }

    private fun mapSourceTypeToSourceTypeMode(
        sources: List<SourceType>,
        selected: SourceType
    ): List<SourceTypeModel> {
        return sources.map {
            val name = when (it) {
                SourceType.MNEMONIC_PASSPHRASE -> resourceManager.getString(R.string.recovery_passphrase)
                SourceType.RAW_SEED -> resourceManager.getString(R.string.recovery_raw_seed)
                SourceType.KEYSTORE -> resourceManager.getString(R.string.recovery_json)
            }

            SourceTypeModel(name, it, selected == it)
        }
    }
}