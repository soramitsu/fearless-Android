package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.mnemonic.model.MnemonicWordModel

class BackupMnemonicViewModel(
    private val accountInteractor: AccountInteractor,
    private val router: AccountRouter,
    private val accountName: String
) : BaseViewModel() {

    private val _mnemonicLiveData = MutableLiveData<Pair<Int, List<MnemonicWordModel>>>()
    val mnemonicLiveData: LiveData<Pair<Int, List<MnemonicWordModel>>> = _mnemonicLiveData

    init {
        disposables.add(
            accountInteractor.getMnemonic()
                .subscribeOn(Schedulers.io())
                .map { mapMnemonicToMnemonicWords(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _mnemonicLiveData.value = it
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
}