package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.mnemonic.model.MnemonicWordModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeModel

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

    init {
        disposables.add(
            interactor.getMnemonic()
                .subscribeOn(Schedulers.io())
                .map { mapMnemonicToMnemonicWords(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _mnemonicLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

        getEncryptionTypesWithSelected()
    }

    private fun getEncryptionTypesWithSelected() {
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
        _encryptionTypesLiveData.value?.let {
            _encryptionTypeChooserEvent.value = Event(it)
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

    fun encryptionTypeChanged(cryptoType: CryptoType) {
        disposables.add(
            interactor.saveSelectedEncryptionType(cryptoType)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    getEncryptionTypesWithSelected()
                }, {
                    it.printStackTrace()
                })
        )
    }
}