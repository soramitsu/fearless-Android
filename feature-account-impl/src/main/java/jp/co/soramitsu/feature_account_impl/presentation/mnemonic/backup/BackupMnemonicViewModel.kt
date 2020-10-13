package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.NetworkChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.mnemonic.model.MnemonicWordModel

class BackupMnemonicViewModel(
    interactor: AccountInteractor,
    private val router: AccountRouter,
    private val accountName: String,
    private val cryptoTypeChooserMixin: CryptoTypeChooserMixin,
    private val networkChooserMixin: NetworkChooserMixin
) : BaseViewModel(),
    CryptoTypeChooserMixin by cryptoTypeChooserMixin,
    NetworkChooserMixin by networkChooserMixin {

    private val _mnemonicLiveData = MutableLiveData<Pair<Int, List<MnemonicWordModel>>>()
    val mnemonicLiveData: LiveData<Pair<Int, List<MnemonicWordModel>>> = _mnemonicLiveData

    private val _showInfoEvent = MutableLiveData<Event<Unit>>()
    val showInfoEvent: LiveData<Event<Unit>> = _showInfoEvent

    private var mnemonic: String = ""

    init {
        disposables += networkDisposable
        disposables += cryptoDisposable

        disposables.add(
            interactor.getMnemonic()
                .subscribeOn(Schedulers.io())
                .doOnSuccess { mnemonic = it.joinToString(" ") }
                .map { mapMnemonicToMnemonicWords(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _mnemonicLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun homeButtonClicked() {
        router.backToCreateAccountScreen()
    }

    fun infoClicked() {
        _showInfoEvent.value = Event(Unit)
    }

    fun nextClicked(derivationPath: String) {
        selectedEncryptionTypeLiveData.value?.cryptoType?.let { cryptoType ->
            selectedNetworkLiveData.value?.let { networkModel ->
                val node = networkModel.defaultNode
                mnemonicLiveData.value?.let {
                    val mnemonic = it.second.map { it.word }
                    router.openConfirmMnemonicScreen(
                        accountName,
                        mnemonic,
                        cryptoType,
                        node,
                        derivationPath
                    )
                }
            }
        }
    }

    private fun mapMnemonicToMnemonicWords(mnemonic: List<String>): Pair<Int, List<MnemonicWordModel>> {
        val words = mnemonic.mapIndexed { index: Int, word: String ->
            MnemonicWordModel(
                (index + 1).toString(),
                word
            )
        }
        val columns = if (words.size % 2 == 0) {
            words.size / 2
        } else {
            words.size / 2 + 1
        }
        return Pair(columns, words)
    }
}