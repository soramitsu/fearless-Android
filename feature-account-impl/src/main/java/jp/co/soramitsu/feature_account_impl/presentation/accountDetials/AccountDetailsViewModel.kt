package jp.co.soramitsu.feature_account_impl.presentation.accountDetials

import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.utils.from
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.common.mapNetworkToNetworkModel

class AccountDetailsViewModel(
    private val accountInteractor: AccountInteractor,
    private val accountRouter: AccountRouter,
    private val clipboardManager: ClipboardManager,
    accountAddress: String
) : BaseViewModel() {
    val account = getAccount(accountAddress).asLiveData()

    val networkModel = account.map { mapNetworkToNetworkModel(it.network) }

    fun backClicked() {
        accountRouter.back()
    }

    private fun getAccount(accountAddress: String): Single<Account> {
        return accountInteractor.getAccount(accountAddress)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun copyAddressClicked() {
        account.value?.let {
            clipboardManager.addToClipboard(it.address)

            showMessage(from(R.string.common_copied))
        }
    }
}
