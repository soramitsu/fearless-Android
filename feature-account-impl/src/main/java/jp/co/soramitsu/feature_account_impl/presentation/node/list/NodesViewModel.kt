package jp.co.soramitsu.feature_account_impl.presentation.node.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.subscribeToError
import jp.co.soramitsu.common.utils.zipSimilar
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.R
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
    private val addressIconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager
) : BaseViewModel(), NodeListingMixin by nodeListingMixin {

    private val _noAccountsEvent = MutableLiveData<Event<Node.NetworkType>>()
    val noAccountsEvent: LiveData<Event<Node.NetworkType>> = _noAccountsEvent

    private val _showAccountChooserLiveData = MutableLiveData<Event<AccountChooserPayload>>()
    val showAccountChooserLiveData: LiveData<Event<AccountChooserPayload>> = _showAccountChooserLiveData

    private val _editMode = MutableLiveData<Boolean>()
    val editMode: LiveData<Boolean> = _editMode

    private val _deleteNodeEvent = MutableLiveData<Event<NodeModel>>()
    val deleteNodeEvent: LiveData<Event<NodeModel>> = _deleteNodeEvent

    val toolbarAction = editMode.map {
        if (it) {
            resourceManager.getString(R.string.common_done)
        } else {
            resourceManager.getString(R.string.common_edit)
        }
    }

    fun editClicked() {
        val edit = editMode.value ?: false
        _editMode.value = !edit
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
                it.message?.let(this::showError)
            })
    }

    fun accountSelected(accountModel: AccountByNetworkModel) {
        selectAccountForNode(accountModel.nodeId, accountModel.accountAddress)
    }

    fun addNodeClicked() {
        router.openAddNode()
    }

    fun createAccountForNetworkType(networkType: Node.NetworkType) {
        router.createAccountForNetworkType(networkType)
    }

    private fun handleAccountsForNetwork(nodeModel: NodeModel, accounts: List<Account>) {
        if (accounts.isEmpty()) {
            _noAccountsEvent.value = Event(nodeModel.networkModelType.networkType)
        } else {
            if (accounts.size == 1) {
                selectAccountForNode(nodeModel.id, accounts.first().address)
            } else {
                showAccountChooser(nodeModel, accounts)
            }
        }
    }

    private fun showAccountChooser(nodeModel: NodeModel, accounts: List<Account>) {
        disposables += generateAccountModels(nodeModel, accounts)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ accountModels ->
                _showAccountChooserLiveData.value = Event(AccountChooserPayload(accountModels, nodeModel.networkModelType))
            }, {
                it.message?.let(this::showError)
            })
    }

    @Suppress("UNCHECKED_CAST")
    private fun generateAccountModels(nodeModel: NodeModel, accounts: List<Account>): Single<List<AccountByNetworkModel>> {
        val singles = accounts.map { mapAccountToAccountModel(nodeModel.id, it) }

        return singles.zipSimilar()
    }

    private fun mapAccountToAccountModel(nodeId: Int, account: Account): Single<AccountByNetworkModel> {
        return generateIconForAddress(account).map {
            AccountByNetworkModel(nodeId, account.address, account.name, it)
        }
    }

    private fun selectAccountForNode(nodeId: Int, accountAddress: String) {
        disposables += interactor.selectNodeAndAccount(nodeId, accountAddress)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeToError {
                it.message?.let { showError(it) }
            }
    }

    private fun generateIconForAddress(account: Account): Single<AddressModel> {
        return interactor.getAddressId(account)
            .flatMap { addressIconGenerator.createAddressModel(account.address, it, ICON_IN_DP) }
    }

    fun deleteNodeClicked(nodeModel: NodeModel) {
        _deleteNodeEvent.value = Event(nodeModel)
    }

    fun confirmNodeDeletion(nodeModel: NodeModel) {
        disposables += interactor.deleteNode(nodeModel.id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeToError {
                it.message?.let { showError(it) }
            }
    }
}