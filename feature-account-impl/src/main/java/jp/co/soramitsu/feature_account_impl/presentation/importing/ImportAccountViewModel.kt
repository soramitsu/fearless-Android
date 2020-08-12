package jp.co.soramitsu.feature_account_impl.presentation.importing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.fearless_utils.exceptions.Bip39Exception
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType
import jp.co.soramitsu.feature_account_api.domain.model.SourceType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.domain.model.PinCodeAction
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.SourceTypeModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeSelectedModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel

class ImportAccountViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

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

    private val _sourceTypeChooserDialogInitialData = MutableLiveData<Event<List<SourceTypeModel>>>()
    val sourceTypeChooserDialogInitialData: LiveData<Event<List<SourceTypeModel>>> = _sourceTypeChooserDialogInitialData

    private val _encryptionTypesLiveData = MutableLiveData<List<CryptoTypeModel>>()
    val encryptionTypesLiveData: LiveData<List<CryptoTypeModel>> = _encryptionTypesLiveData

    private val _encryptionTypeChooserEvent = MutableLiveData<Event<List<CryptoTypeModel>>>()
    val encryptionTypeChooserEvent: LiveData<Event<List<CryptoTypeModel>>> = _encryptionTypeChooserEvent

    private val _selectedEncryptionTypeLiveData = MediatorLiveData<CryptoTypeSelectedModel>()
    val selectedEncryptionTypeLiveData: LiveData<CryptoTypeSelectedModel> = _selectedEncryptionTypeLiveData

    private val _networksLiveData = MutableLiveData<List<NetworkModel>>()
    val networksLiveData: LiveData<List<NetworkModel>> = _networksLiveData

    private val _networkChooserEvent = MutableLiveData<Event<List<NetworkModel>>>()
    val networkChooserEvent: LiveData<Event<List<NetworkModel>>> = _networkChooserEvent

    private val _selectedNetworkLiveData = MediatorLiveData<NetworkModel>()
    val selectedNetworkLiveData: LiveData<NetworkModel> = _selectedNetworkLiveData

    init {
        _selectedSourceTypeLiveData.addSource(sourceTypesLiveData) {
            val selected = it.firstOrNull { it.isSelected } ?: it.first()
            _selectedSourceTypeLiveData.value = selected
        }

        _selectedEncryptionTypeLiveData.addSource(encryptionTypesLiveData) {
            val selected = it.firstOrNull { it.isSelected } ?: it.first()
            val encryptionName = getEncryptionTypeNameForCryptoType(selected.cryptoType)
            _selectedEncryptionTypeLiveData.value = CryptoTypeSelectedModel(encryptionName, selected.cryptoType)
        }

        _selectedNetworkLiveData.addSource(networksLiveData) {
            val selected = it.firstOrNull { it.isSelected } ?: it.first()
            _selectedNetworkLiveData.value = selected
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

        disposables.add(
            interactor.getEncryptionTypesWithSelected()
                .subscribeOn(Schedulers.io())
                .map { mapEncryptionTypeToEncryptionTypeModel(it.first, it.second) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _encryptionTypesLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

        disposables.add(
            interactor.getNetworksWithSelected()
                .subscribeOn(Schedulers.io())
                .map { mapNetworkToNetworkModel(it.first, it.second) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _networksLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    private fun mapSourceTypeToSourceTypeMode(sources: List<SourceType>, selected: SourceType): List<SourceTypeModel> {
        return sources.map {
            val name = when (it) {
                SourceType.MNEMONIC_PASSPHRASE -> resourceManager.getString(R.string.recovery_passphrase)
                SourceType.RAW_SEED -> resourceManager.getString(R.string.recovery_raw_seed)
                SourceType.KEYSTORE -> resourceManager.getString(R.string.recovery_json)
            }

            SourceTypeModel(name, it, selected == it)
        }
    }

    private fun mapEncryptionTypeToEncryptionTypeModel(encryptionTypes: List<CryptoType>, selected: CryptoType): List<CryptoTypeModel> {
        return encryptionTypes.map {
            val name = when (it) {
                CryptoType.SR25519 -> "${resourceManager.getString(R.string.sr25519_selection_title)} | ${resourceManager.getString(R.string.sr25519_selection_subtitle)}"
                CryptoType.ED25519 -> "${resourceManager.getString(R.string.ed25519_selection_title)} | ${resourceManager.getString(R.string.ed25519_selection_subtitle)}"
                CryptoType.ECDSA -> "${resourceManager.getString(R.string.ecdsa_selection_title)} | ${resourceManager.getString(R.string.ecdsa_selection_subtitle)}"
            }
            val isSelected = it == selected
            CryptoTypeModel(name, it, isSelected)
        }
    }

    private fun mapNetworkToNetworkModel(networks: List<Network>, selected: NetworkType): List<NetworkModel> {
        return networks.map {
            val icon = when (it.networkType) {
                NetworkType.POLKADOT -> R.drawable.ic_ksm_24
                NetworkType.KUSAMA -> R.drawable.ic_ksm_24
                NetworkType.WESTEND -> R.drawable.ic_westend_24
            }
            val isSelected = selected == it.networkType
            NetworkModel(it.name, icon, it.link, it.networkType, isSelected)
        }
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

    fun encryptionTypeInputClicked() {
        encryptionTypesLiveData.value?.let {
            _encryptionTypeChooserEvent.value = Event(it)
        }
    }

    fun networkInputClicked() {
        networksLiveData.value?.let {
            _networkChooserEvent.value = Event(it)
        }
    }

    fun encryptionTypeChanged(cryptoType: CryptoType) {
        val encryptionName = getEncryptionTypeNameForCryptoType(cryptoType)
        _selectedEncryptionTypeLiveData.value = CryptoTypeSelectedModel(encryptionName, cryptoType)
    }

    private fun getEncryptionTypeNameForCryptoType(cryptoType: CryptoType): String {
        return when (cryptoType) {
            CryptoType.SR25519 -> "${resourceManager.getString(R.string.sr25519_selection_title)} | ${resourceManager.getString(R.string.sr25519_selection_subtitle)}"
            CryptoType.ED25519 -> "${resourceManager.getString(R.string.ed25519_selection_title)} | ${resourceManager.getString(R.string.ed25519_selection_subtitle)}"
            CryptoType.ECDSA -> "${resourceManager.getString(R.string.ecdsa_selection_title)} | ${resourceManager.getString(R.string.ecdsa_selection_subtitle)}"
        }
    }

    fun qrScanClicked() {
        _qrScanStartLiveData.value = Event(Unit)
    }

    fun nextBtnClicked(keyString: String, username: String, password: String, json: String, derivationPath: String) {
        selectedNetworkLiveData.value?.networkType?.let { networkType ->
            selectedSourceTypeLiveData.value?.sourceType?.let { sourceType ->
                selectedEncryptionTypeLiveData.value?.cryptoType?.let { cryptoType ->
                    val importDisposable = when (sourceType) {
                        SourceType.MNEMONIC_PASSPHRASE -> interactor.importFromMnemonic(keyString, username, derivationPath, cryptoType, networkType)
                        SourceType.RAW_SEED -> interactor.importFromSeed(keyString, username, derivationPath, cryptoType, networkType)
                        SourceType.KEYSTORE -> interactor.importFromJson(json, password, networkType)
                    }

                    disposables.add(
                        importDisposable
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({
                                router.showPincode(PinCodeAction.CREATE_PIN_CODE)
                            }, {
                                if (it is Bip39Exception) {
                                    onError(R.string.access_restore_phrase_error_message)
                                } else {
                                    onError(R.string.common_undefined_error_message)
                                }
                            })
                    )
                }
            }
        }
    }

    fun networkChanged(networkModel: NetworkModel) {
        _selectedNetworkLiveData.value = networkModel
    }

    fun inputChanges(input1: String, input2: String) {
        _nextButtonEnabledLiveData.value = input1.isNotEmpty() && input2.isNotEmpty()
    }
}