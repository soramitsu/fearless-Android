package jp.co.soramitsu.feature_account_impl.presentation.importing

import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.switchMap
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.NetworkChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.FileRequester
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportError
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.JsonImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.MnemonicImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.RawSeedImportSource

class ImportAccountViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val resourceManager: ResourceManager,
    private val cryptoTypeChooserMixin: CryptoTypeChooserMixin,
    private val networkChooserMixin: NetworkChooserMixin,
    private val clipboardManager: ClipboardManager,
    private val fileReader: FileReader
) : BaseViewModel(),
    CryptoTypeChooserMixin by cryptoTypeChooserMixin,
    NetworkChooserMixin by networkChooserMixin {

    val nameLiveData = MutableLiveData<String>()

    val sourceTypes = provideSourceType()

    private val _selectedSourceTypeLiveData = MutableLiveData<ImportSource>()

    val selectedSourceTypeLiveData: LiveData<ImportSource> = _selectedSourceTypeLiveData

    private val _showSourceChooserLiveData = MutableLiveData<Event<Payload<ImportSource>>>()
    val showSourceSelectorChooserLiveData: LiveData<Event<Payload<ImportSource>>> = _showSourceChooserLiveData

    val derivationPathLiveData = MutableLiveData<String>()

    private val sourceTypeValid = _selectedSourceTypeLiveData.switchMap(ImportSource::validationLiveData)

    private val importInProgressLiveData = MutableLiveData<Boolean>(false)

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

    val networkChooserEnabledLiveData = _selectedSourceTypeLiveData.switchMap {
        if (it is JsonImportSource) {
            it.enableNetworkInputLiveData
        } else {
            MutableLiveData(true)
        }
    }

    val advancedBlockExceptNetworkEnabled = _selectedSourceTypeLiveData.map { it !is JsonImportSource }

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
            _showSourceChooserLiveData.value = Event(Payload(sourceTypes, it))
        }
    }

    fun sourceTypeChanged(it: ImportSource) {
        _selectedSourceTypeLiveData.value = it
    }

    fun nextClicked() {
        importInProgressLiveData.value = true

        val sourceType = selectedSourceTypeLiveData.value!!

        val networkType = selectedNetworkLiveData.value!!.networkTypeUI.networkType
        val cryptoType = selectedEncryptionTypeLiveData.value!!.cryptoType
        val derivationPath = derivationPathLiveData.value.orEmpty()
        val name = nameLiveData.value!!

        val importObservable = constructImportObservable(sourceType, name, derivationPath, cryptoType, networkType)

        disposables += importObservable
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally { importInProgressLiveData.value = false }
            .subscribe(::continueBasedOnCodeStatus, ::handleCreateAccountError)
    }

    fun systemCallResultReceived(requestCode: Int, intent: Intent) {
        val selectedSource = selectedSourceTypeLiveData.value!!

        if (selectedSource is FileRequester) {
            val currentRequestCode = selectedSource.chooseJsonFileEvent.value!!.peekContent()

            if (requestCode == currentRequestCode) {
                selectedSource.fileChosen(intent.data!!)
            }
        }
    }

    private fun continueBasedOnCodeStatus() {
        if (interactor.isCodeSet()) {
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
            JsonImportSource(
                networkChooserMixin.selectedNetworkLiveData,
                nameLiveData,
                cryptoTypeChooserMixin.selectedEncryptionTypeLiveData,
                interactor,
                resourceManager,
                clipboardManager,
                fileReader,
                disposables
            ),
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
                networkType,
                name
            )
        }
    }
}