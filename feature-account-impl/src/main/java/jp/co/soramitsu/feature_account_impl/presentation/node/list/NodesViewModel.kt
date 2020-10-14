package jp.co.soramitsu.feature_account_impl.presentation.node.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.subscribeToError
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.node.list.accounts.AccountChooserPayload
import jp.co.soramitsu.feature_account_impl.presentation.node.list.accounts.model.AccountByNetworkModel
import jp.co.soramitsu.feature_account_impl.presentation.node.mixin.api.NodeListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel

private const val ICON_IN_DP = 24

class NodesViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val nodeListingMixin: NodeListingMixin,
    private val addressIconGenerator: AddressIconGenerator
) : BaseViewModel(), NodeListingMixin by nodeListingMixin {

    private val _noAccountsEvent = MutableLiveData<Event<Node.NetworkType>>()
    val noAccountsEvent: LiveData<Event<Node.NetworkType>> = _noAccountsEvent

    private val _showAccountChooserLiveData = MutableLiveData<Event<AccountChooserPayload>>()
    val showAccountChooserLiveData: LiveData<Event<AccountChooserPayload>> = _showAccountChooserLiveData

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
                handleAccountsForNetwork(nodeModel, accounts)
            }, {
                it.message?.let { showError(it) }
            })
    }

    private fun handleAccountsForNetwork(nodeModel: NodeModel, accounts: List<Account>) {
        if (accounts.isEmpty()) {
            _noAccountsEvent.value = Event(nodeModel.networkModelType.networkType)
        } else {
            if (accounts.size == 1) {
                selectAccountForNode(nodeModel.id, accounts.first().address)
            } else {
                val accountModels = accounts.map { mapAccountToAccountModel(nodeModel.id, it) }
                _showAccountChooserLiveData.value = Event(AccountChooserPayload(accountModels, nodeModel.networkModelType))
            }
        }
    }

    private fun mapAccountToAccountModel(nodeId: Int, account: Account): AccountByNetworkModel {
        return AccountByNetworkModel(nodeId, account.address, account.name, generateIconForAddress(account))
    }

    fun accountSelected(accountModel: AccountByNetworkModel) {
        selectAccountForNode(accountModel.nodeId, accountModel.accountAddress)
    }

    private fun selectAccountForNode(nodeId: Int, accountAddress: String) {
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

    fun createAccountForNetworkType(networkType: Node.NetworkType) {
        router.createAccountForNetworkType(networkType)
    }

    private fun generateIconForAddress(account: Account): AddressModel {
        return interactor.getAddressId(account)
            .flatMap { addressIconGenerator.createAddressIcon(account.address, it, ICON_IN_DP) }
            .blockingGet()
    }
}