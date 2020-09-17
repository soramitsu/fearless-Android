package jp.co.soramitsu.feature_account_impl.presentation.importing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.switchMap
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.NetworkChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.SourceSelectorPayload
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.JsonImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.MnemonicImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.RawSeedImportSource

class ImportAccountViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val resourceManager: ResourceManager,
    private val cryptoTypeChooserMixin: CryptoTypeChooserMixin,
    private val networkChooserMixin: NetworkChooserMixin
) : BaseViewModel(),
    CryptoTypeChooserMixin by cryptoTypeChooserMixin,
    NetworkChooserMixin by networkChooserMixin {

    private val _qrScanStartLiveData = MutableLiveData<Event<Unit>>()
    val qrScanStartLiveData: LiveData<Event<Unit>> = _qrScanStartLiveData

    val sourceTypes = provideSourceType()

    val nameLiveData = MutableLiveData<String>()

    private val _selectedSourceTypeLiveData = MutableLiveData<ImportSource>()

    val selectedSourceTypeLiveData: LiveData<ImportSource> = _selectedSourceTypeLiveData
    private val _showSourceChooserLiveData = MutableLiveData<Event<SourceSelectorPayload>>()

    val showSourceChooserLiveData: LiveData<Event<SourceSelectorPayload>> = _showSourceChooserLiveData

    val derivationPathLiveData = MutableLiveData<String>()

    private val sourceTypeValid = _selectedSourceTypeLiveData.switchMap(ImportSource::validationLiveData)

    val nextButtonEnabledLiveData = sourceTypeValid.combine(nameLiveData) { sourceTypeValid, name ->
        sourceTypeValid && name.isNotEmpty()
    }

    init {
        disposables += networkDisposable
        disposables += cryptoDisposable

        _selectedSourceTypeLiveData.value = sourceTypes.first()
    }

    fun homeButtonClicked() {
        router.backToWelcomeScreen()
    }

    fun openSourceChooserClicked() {
        selectedSourceTypeLiveData.value?.let {
            _showSourceChooserLiveData.value = Event(SourceSelectorPayload(sourceTypes, it))
        }
    }

    fun sourceTypeChanged(it: ImportSource) {
        _selectedSourceTypeLiveData.value = it
    }

    fun qrScanClicked() {
        _qrScanStartLiveData.value = Event(Unit)
    }

    fun nextClicked() {
        val sourceType = selectedSourceTypeLiveData.value!!

        val node = selectedNetworkLiveData.value?.defaultNode!!
        val cryptoType = selectedEncryptionTypeLiveData.value!!.cryptoType
        val derivationPath = derivationPathLiveData.value.orEmpty()
        val name = nameLiveData.value!!

        val importObservable = constructImportObservable(sourceType, name, derivationPath, cryptoType, node)

        disposables.add(
            importObservable
                .andThen(interactor.isCodeSet())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(::continueBasedOnCodeStatus, ::handleCreateAccountError)
        )
    }

    private fun continueBasedOnCodeStatus(isCodeSet: Boolean) {
        if (isCodeSet) {
            router.openMain()
        } else {
            router.openCreatePincode()
        }
    }

    private fun handleCreateAccountError(throwable: Throwable) {
        var errorMessage = selectedSourceTypeLiveData.value?.handleError(throwable)

        if (errorMessage == null) {
            errorMessage = when (throwable) {
                is AccountAlreadyExistsException -> R.string.account_add_already_exists_message
                else -> R.string.common_undefined_error_message
            }
        }

        showError(resourceManager.getString(errorMessage))
    }

    private fun provideSourceType(): List<ImportSource> {
        return listOf(
            JsonImportSource(),
            MnemonicImportSource(),
            RawSeedImportSource()
        )
    }

    private fun constructImportObservable(sourceType: ImportSource, name: String, derivationPath: String, cryptoType: CryptoType, node: Node): Completable {
        return when (sourceType) {
            is MnemonicImportSource -> interactor.importFromMnemonic(
                sourceType.mnemonicContentLiveData.value!!,
                name,
                derivationPath,
                cryptoType,
                node
            )
            is RawSeedImportSource -> interactor.importFromSeed(
                sourceType.rawSeedLiveData.value!!,
                name,
                derivationPath,
                cryptoType,
                node
            )
            is JsonImportSource -> interactor.importFromJson(
                sourceType.jsonContentLiveData.value!!,
                name,
                node.networkType
            )
        }
    }

    fun fileChosen(fileContent: String?) {
        val jsonSource = selectedSourceTypeLiveData.value as? JsonImportSource

        if (jsonSource != null && fileContent != null) {
            jsonSource.jsonContentLiveData.value = fileContent
        }
    }
}