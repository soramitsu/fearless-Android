package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.domain.model.PinCodeAction
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.mnemonic.model.MnemonicWordModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeSelectedModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel

class BackupMnemonicViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val accountName: String,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val _mnemonicLiveData = MutableLiveData<Pair<Int, List<MnemonicWordModel>>>()
    val mnemonicLiveData: LiveData<Pair<Int, List<MnemonicWordModel>>> = _mnemonicLiveData

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

    private var mnemonic: String = ""

    init {
        disposables.add(
            interactor.getMnemonic()
                .subscribeOn(Schedulers.io())
                .doOnSuccess {
                    mnemonic = it.joinToString(" ")
                }
                .map { mapMnemonicToMnemonicWords(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _mnemonicLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

        _selectedEncryptionTypeLiveData.addSource(encryptionTypesLiveData) {
            val selected = it.firstOrNull { it.isSelected } ?: it.first()
            val encryptionName = getEncryptionTypeNameForCryptoType(selected.cryptoType)
            _selectedEncryptionTypeLiveData.value = CryptoTypeSelectedModel(encryptionName, selected.cryptoType)
        }

        _selectedNetworkLiveData.addSource(networksLiveData) {
            val selected = it.firstOrNull { it.isSelected } ?: it.first()
            _selectedNetworkLiveData.value = selected
        }

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

    private fun mapMnemonicToMnemonicWords(mnemonic: List<String>): Pair<Int, List<MnemonicWordModel>> {
        val words = mnemonic.mapIndexed { index: Int, word: String -> MnemonicWordModel((index + 1).toString(), word) }
        val columns = if (words.size % 2 == 0) {
            words.size / 2
        } else {
            words.size / 2 + 1
        }
        return Pair(columns, words)
    }

    fun homeButtonClicked() {
        router.backToCreateAccountScreen()
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

    fun networkChanged(networkModel: NetworkModel) {
        _selectedNetworkLiveData.value = networkModel
    }

    fun nextClicked(derivationPath: String) {
        selectedEncryptionTypeLiveData.value?.cryptoType?.let { cryptoType ->
            selectedNetworkLiveData.value?.networkType?.let { networkType ->
                disposables.add(
                    interactor.createAccount(accountName, mnemonic, cryptoType, derivationPath, networkType)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            router.showPincode(PinCodeAction.CREATE_PIN_CODE)
                        }, {
                            it.printStackTrace()
                        })
                )
            }
        }
    }
}