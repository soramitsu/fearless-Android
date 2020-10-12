package jp.co.soramitsu.feature_account_impl.presentation.node.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.subscribeToError
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.node.mixin.api.NodeListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel

class NodesViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val nodeListingMixin: NodeListingMixin
) : BaseViewModel(), NodeListingMixin by nodeListingMixin {

    private val _noAccountsEvent = MutableLiveData<Event<Unit>>()
    val noAccountsEvent: LiveData<Event<Unit>> = _noAccountsEvent

    fun editClicked() {
        // TODO
    }

    fun backClicked() {
        router.back()
    }

    fun infoClicked(nodeModel: NodeModel) {
        router.openNodeDetails(nodeModel.id)
    }

    fun selectNodeClicked(nodeModel: NodeModel) {
        disposables += interactor.getAccountsByNetworkType(nodeModel.networkModelType.networkType)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ accounts ->
                handleAccountsForNetwork(nodeModel.id, accounts)
            }, {
                it.message?.let { showError(it) }
            })
    }

    private fun handleAccountsForNetwork(nodeId: Int, accounts: List<Account>) {
        if (accounts.isEmpty()) {
            _noAccountsEvent.value = Event(Unit)
        } else {
            if (accounts.size == 1) {
                selectAccountForNode(nodeId, accounts.first().address)
            } else {

            }
        }
    }

    fun selectAccountForNode(nodeId: Int, accountAddress: String) {
        disposables += interactor.selectNodeAndAccount(nodeId, accountAddress)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeToError {
                it.message?.let { showError(it) }
            }
    }

    fun addNodeClicked() {
        router.openAddNode()
    }

    fun createAccount() {
        router.createAccountForNetworkType()
    }
}