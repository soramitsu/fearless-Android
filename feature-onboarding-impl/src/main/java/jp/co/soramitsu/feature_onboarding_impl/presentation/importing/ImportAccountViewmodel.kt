package jp.co.soramitsu.feature_onboarding_impl.presentation.importing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.fearless_utils.exceptions.Bip39Exception
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SourceType
import jp.co.soramitsu.feature_onboarding_api.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.presentation.importing.encryption.model.CryptoTypeModel
import jp.co.soramitsu.feature_onboarding_impl.presentation.importing.network.model.NodeModel
import jp.co.soramitsu.feature_onboarding_impl.presentation.importing.source.model.SourceTypeModel

class ImportAccountViewmodel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val _usernameVisibilityLiveData = MutableLiveData<Boolean>()
    val usernameVisibilityLiveData: LiveData<Boolean> = _usernameVisibilityLiveData

    private val _passwordVisibilityLiveData = MutableLiveData<Boolean>()
    val passwordVisibilityLiveData: LiveData<Boolean> = _passwordVisibilityLiveData

    private val _jsonInputVisibilityLiveData = MutableLiveData<Boolean>()
    val jsonInputVisibilityLiveData: LiveData<Boolean> = _jsonInputVisibilityLiveData

    private val _advancedVisibilityLiveData = MutableLiveData<Boolean>()
    val advancedVisibilityLiveData: LiveData<Boolean> = _advancedVisibilityLiveData

    private val _qrScanStartLiveData = MutableLiveData<Event<Unit>>()
    val qrScanStartLiveData: LiveData<Event<Unit>> = _qrScanStartLiveData

    private val _nextButtonEnabledLiveData = MutableLiveData<Boolean>()
    val nextButtonEnabledLiveData: LiveData<Boolean> = _nextButtonEnabledLiveData

    private val _sourceTypeChooserDialogInitialData = MutableLiveData<List<SourceTypeModel>>()
    val sourceTypeChooserDialogInitialData: LiveData<List<SourceTypeModel>> = _sourceTypeChooserDialogInitialData

    private val _encryptionTypeChooserDialogInitialData = MutableLiveData<List<CryptoTypeModel>>()
    val encryptionTypeChooserDialogInitialData: LiveData<List<CryptoTypeModel>> = _encryptionTypeChooserDialogInitialData

    private val _networkTypeChooserDialogInitialData = MutableLiveData<List<NodeModel>>()
    val networkTypeChooserDialogInitialData: LiveData<List<NodeModel>> = _networkTypeChooserDialogInitialData

    private val _selectedSourceTypeText = MutableLiveData<String>()
    val selectedSourceTypeText: LiveData<String> = _selectedSourceTypeText

    private val _selectedEncryptionTypeText = MutableLiveData<String>()
    val selectedEncryptionTypeText: LiveData<String> = _selectedEncryptionTypeText

    private val _selectedNodeText = MutableLiveData<String>()
    val selectedNodeText: LiveData<String> = _selectedNodeText

    private val _selectedNodeIcon = MutableLiveData<Int>()
    val selectedNodeIcon: LiveData<Int> = _selectedNodeIcon

    private val selectedNodeLiveData = MutableLiveData<Node>()
    private var selectedSourceTypeLiveData = MutableLiveData<SourceType>()
    private var selectedEncryptionTypeLiveData = MutableLiveData<CryptoType>()

    init {
        disposables.add(
            interactor.getDefaultNodes()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    networkTypeChanged(mapNetworkTypeToNodeModel(it.first()))
                }, {
                    it.printStackTrace()
                })
        )

        disposables.add(
            interactor.getSourceTypes()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    sourceTypeChanged(it.first())
                }, {
                    it.printStackTrace()
                })
        )

        disposables.add(
            interactor.getEncryptionTypes()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    encryptionTypeChanged(it.first())
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun homeButtonClicked() {
        router.backToWelcomeScreen()
    }

    fun advancedButtonClicked() {
        _advancedVisibilityLiveData.value = _advancedVisibilityLiveData.value?.not() ?: true
    }

    fun sourceTypeInputClicked() {
        disposables.add(
            interactor.getSourceTypes()
                .subscribeOn(Schedulers.io())
                .map { it.map { mapSourceTypeToSourceTypeModel(it) } }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _sourceTypeChooserDialogInitialData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun sourceTypeChanged(it: SourceType) {
        selectedSourceTypeLiveData.value = it

        when (it) {
            SourceType.MNEMONIC_PASSPHRASE -> {
                _usernameVisibilityLiveData.value = true
                _passwordVisibilityLiveData.value = false
                _jsonInputVisibilityLiveData.value = false
                _selectedSourceTypeText.value = resourceManager.getString(R.string.recovery_passphrase)
            }
            SourceType.RAW_SEED -> {
                _usernameVisibilityLiveData.value = true
                _passwordVisibilityLiveData.value = false
                _jsonInputVisibilityLiveData.value = false
                _selectedSourceTypeText.value = resourceManager.getString(R.string.recovery_raw_seed)
            }
            SourceType.KEYSTORE -> {
                _usernameVisibilityLiveData.value = false
                _passwordVisibilityLiveData.value = true
                _jsonInputVisibilityLiveData.value = true
                _selectedSourceTypeText.value = resourceManager.getString(R.string.recovery_json)
            }
        }

        _advancedVisibilityLiveData.value = false
    }

    fun encryptionTypeInputClicked() {
        disposables.add(
            interactor.getEncryptionTypes()
                .subscribeOn(Schedulers.io())
                .map { it.map { mapEncryptionTypeToEncryptionTypeModel(it) } }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _encryptionTypeChooserDialogInitialData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun encryptionTypeChanged(it: CryptoType) {
        selectedEncryptionTypeLiveData.value = it

        _selectedEncryptionTypeText.value = when (it) {
            CryptoType.SR25519 -> "${resourceManager.getString(R.string.sr25519_selection_title)} | ${resourceManager.getString(R.string.sr25519_selection_subtitle)}"
            CryptoType.ED25519 -> "${resourceManager.getString(R.string.ed25519_selection_title)} | ${resourceManager.getString(R.string.ed25519_selection_subtitle)}"
            CryptoType.ECDSA -> "${resourceManager.getString(R.string.ecdsa_selection_title)} | ${resourceManager.getString(R.string.ecdsa_selection_subtitle)}"
        }
    }

    fun networkTypeInputClicked() {
        disposables.add(
            interactor.getDefaultNodes()
                .subscribeOn(Schedulers.io())
                .map { it.map { mapNetworkTypeToNodeModel(it) } }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _networkTypeChooserDialogInitialData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun networkTypeChanged(it: NodeModel) {
        selectedNodeLiveData.value = mapNodeModelToNode(it)

        _selectedNodeText.value = it.name
        _selectedNodeIcon.value = it.icon
    }

    private fun mapSourceTypeToSourceTypeModel(sourceType: SourceType): SourceTypeModel {
        val name = when (sourceType) {
            SourceType.MNEMONIC_PASSPHRASE -> resourceManager.getString(R.string.recovery_passphrase)
            SourceType.RAW_SEED -> resourceManager.getString(R.string.recovery_raw_seed)
            SourceType.KEYSTORE -> resourceManager.getString(R.string.recovery_json)
        }

        return SourceTypeModel(name, sourceType, sourceType == selectedSourceTypeLiveData.value)
    }

    private fun mapEncryptionTypeToEncryptionTypeModel(encryptionType: CryptoType): CryptoTypeModel {
        val name = when (encryptionType) {
            CryptoType.SR25519 -> "${resourceManager.getString(R.string.sr25519_selection_title)} | ${resourceManager.getString(R.string.sr25519_selection_subtitle)}"
            CryptoType.ED25519 -> "${resourceManager.getString(R.string.ed25519_selection_title)} | ${resourceManager.getString(R.string.ed25519_selection_subtitle)}"
            CryptoType.ECDSA -> "${resourceManager.getString(R.string.ecdsa_selection_title)} | ${resourceManager.getString(R.string.ecdsa_selection_subtitle)}"
        }

        return CryptoTypeModel(name, encryptionType, encryptionType == selectedEncryptionTypeLiveData.value)
    }

    private fun mapNetworkTypeToNodeModel(node: Node): NodeModel {
        val icon = when (node.networkType) {
            NetworkType.POLKADOT -> R.drawable.ic_ksm
            NetworkType.KUSAMA -> R.drawable.ic_ksm
            NetworkType.WESTEND -> R.drawable.ic_westend
            NetworkType.UNKNOWN -> R.drawable.ic_ksm
        }

        return NodeModel(node.name, node.link == selectedNodeLiveData.value?.link, icon, node.link, node.networkType)
    }

    private fun mapNodeModelToNode(node: NodeModel): Node {
        return Node(node.name, node.networkType, node.link)
    }

    fun qrScanClicked() {
        _qrScanStartLiveData.value = Event(Unit)
    }

    fun nextBtnClicked(keyString: String, username: String, password: String, json: String, derivationPath: String) {
        selectedNodeLiveData.value?.let { node ->
            selectedSourceTypeLiveData.value?.let { sourceType ->
                selectedEncryptionTypeLiveData.value?.let { cryptoType ->
                    val disposable = when (sourceType) {
                        SourceType.MNEMONIC_PASSPHRASE -> interactor.importFromMnemonic(keyString, username, derivationPath, cryptoType, node)
                        SourceType.RAW_SEED -> interactor.importFromSeed(keyString, username, derivationPath, cryptoType, node)
                        SourceType.KEYSTORE -> interactor.importFromJson(json, password, node)
                    }

                    disposables.add(disposable
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({}, {
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

    fun inputChanges(input1: String, input2: String) {
        _nextButtonEnabledLiveData.value = input1.isNotEmpty() && input2.isNotEmpty()
    }
}