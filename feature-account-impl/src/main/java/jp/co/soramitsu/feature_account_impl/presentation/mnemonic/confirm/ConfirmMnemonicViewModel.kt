package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

class ConfirmMnemonicViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val _mnemonicLiveData = MutableLiveData<List<String>>()
    val mnemonicLiveData: LiveData<List<String>> = _mnemonicLiveData

    init {
        disposables.add(
            interactor.getMnemonic()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _mnemonicLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }
}