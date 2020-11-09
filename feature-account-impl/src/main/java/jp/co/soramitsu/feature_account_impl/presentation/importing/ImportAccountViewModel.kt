package jp.co.soramitsu.feature_account_impl.presentation.importing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.DEFAULT_ERROR_HANDLER
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.switchMap
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.ImportJsonData
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.common.accountSource.SourceTypeChooserPayload
import jp.co.soramitsu.feature_account_impl.presentation.common.mapCryptoTypeToCryptoTypeModel
import jp.co.soramitsu.feature_account_impl.presentation.common.mapNetworkTypeToNetworkModel
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.NetworkChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportError
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.JsonImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.MnemonicImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.RawSeedImportSource

typealias ImportSourceSelectorPayload = SourceTypeChooserPayload<ImportSource>

class ImportAccountViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val resourceManager: ResourceManager,
    private val cryptoTypeChooserMixin: CryptoTypeChooserMixin,
    private val networkChooserMixin: NetworkChooserMixin
) : BaseViewModel(),
    CryptoTypeChooserMixin by cryptoTypeChooserMixin,
    NetworkChooserMixin by networkChooserMixin {

    val sourceTypes = provideSourceType()

    val nameLiveData = MutableLiveData<String>()

    private val _selectedSourceTypeLiveData = MutableLiveData<ImportSource>()

    val selectedSourceTypeLiveData: LiveData<ImportSource> = _selectedSourceTypeLiveData

    private val _showSourceChooserLiveData = MutableLiveData<Event<ImportSourceSelectorPayload>>()
    val showSourceSelectorChooserLiveData: LiveData<Event<ImportSourceSelectorPayload>> = _showSourceChooserLiveData

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
            _showSourceChooserLiveData.value = Event(SourceTypeChooserPayload(sourceTypes, it))
        }
    }

    fun sourceTypeChanged(it: ImportSource) {
        _selectedSourceTypeLiveData.value = it
    }

    fun nextClicked() {
        val sourceType = selectedSourceTypeLiveData.value!!

        val networkType = selectedNetworkLiveData.value!!.networkTypeUI.networkType
        val cryptoType = selectedEncryptionTypeLiveData.value!!.cryptoType
        val derivationPath = derivationPathLiveData.value.orEmpty()
        val name = nameLiveData.value!!

        val importObservable = constructImportObservable(sourceType, name, derivationPath, cryptoType, networkType)

        disposables += importObservable
            .andThen(interactor.isCodeSet())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(::continueBasedOnCodeStatus, ::handleCreateAccountError)
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
                is AccountAlreadyExistsException -> ImportError(
                    titleRes = R.string.import_account_exists_title,
                    messageRes = R.string.error_try_another_one
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
            JsonImportSource(),
            RawSeedImportSource()
        )
    }

    private fun constructImportObservable(
        sourceType: ImportSource,
        name: String,
        derivationPath: String,
        cryptoType: CryptoType,
        networkType: Node.NetworkType
    ): Completable {
        return when (sourceType) {
            is MnemonicImportSource -> interactor.importFromMnemonic(
                sourceType.mnemonicContentLiveData.value!!,
                name,
                derivationPath,
                cryptoType,
                networkType
            )
            is RawSeedImportSource -> interactor.importFromSeed(
                sourceType.rawSeedLiveData.value!!,
                name,
                derivationPath,
                cryptoType,
                networkType
            )
            is JsonImportSource -> interactor.importFromJson(
                sourceType.jsonContentLiveData.value!!,
                sourceType.passwordLiveData.value!!,
                name
            )
        }
    }

    fun jsonChanged(newJson: String) {
        disposables += interactor.processAccountJson(newJson)
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(::handleParsedImportData, DEFAULT_ERROR_HANDLER)
    }

    private fun handleParsedImportData(it: ImportJsonData) {
        val networkModel = mapNetworkTypeToNetworkModel(it.networkType)
        selectedNetworkLiveData.value = networkModel

        val cryptoModel = mapCryptoTypeToCryptoTypeModel(resourceManager, it.encryptionType)
        selectedEncryptionTypeLiveData.value = cryptoModel

        nameLiveData.value = it.name
    }

    fun fileChosen(fileContent: String?) {
        val jsonSource = selectedSourceTypeLiveData.value as? JsonImportSource

        if (jsonSource != null && fileContent != null) {
            jsonSource.jsonContentLiveData.value = fileContent
        }
    }
}