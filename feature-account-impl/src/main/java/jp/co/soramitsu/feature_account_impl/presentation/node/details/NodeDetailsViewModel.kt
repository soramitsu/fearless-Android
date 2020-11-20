package jp.co.soramitsu.feature_account_impl.presentation.node.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.FearlessException
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.setValueIfNew
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.domain.errors.NodeAlreadyExistsException
import jp.co.soramitsu.feature_account_impl.domain.errors.UnsupportedNetworkException
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

class NodeDetailsViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val nodeId: Int,
    private val isSelected: Boolean,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    val nodeLiveData = getNode(nodeId).asLiveData()

    val editEnabled = nodeLiveData.map(::mapNodeEditState)

    private val _updateButtonEnabled = MutableLiveData<Boolean>()
    val updateButtonEnabled: LiveData<Boolean> = _updateButtonEnabled

    fun backClicked() {
        router.back()
    }

    private fun getNode(nodeId: Int): Single<Node> {
        return interactor.getNode(nodeId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun mapNodeEditState(node: Node): Boolean {
        return !node.isDefault
    }

    fun nodeDetailsEdited() {
        _updateButtonEnabled.setValueIfNew(true)
    }

    fun copyNodeHostClicked() {
        nodeLiveData.value?.let {
            clipboardManager.addToClipboard(it.link)

            showMessage(resourceManager.getString(R.string.common_copied))
        }
    }

    fun updateClicked(name: String, hostUrl: String) {
        disposables += interactor.updateNode(nodeId, name, hostUrl)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                router.back()
            }, ::handleUpdateNodeException)
    }

    private fun handleUpdateNodeException(throwable: Throwable) {
        when (throwable) {
            is NodeAlreadyExistsException -> showError(resourceManager.getString(R.string.connection_add_already_exists_error))
            is UnsupportedNetworkException -> showError(getUnsupportedNodeError())
            is FearlessException -> {
                if (FearlessException.Kind.NETWORK == throwable.kind) {
                    showError(resourceManager.getString(R.string.connection_add_invalid_error))
                } else {
                    throwable.message?.let { showError(it) }
                }
            }
            else -> throwable.message?.let { showError(it) }
        }
    }

    private fun getUnsupportedNodeError(): String {
        val supportedNodes = Node.NetworkType.values().joinToString(",") { it.readableName }
        val unsupportedNodeErrorMsg = resourceManager.getString(R.string.connection_add_unsupported_error)
        return unsupportedNodeErrorMsg.format(supportedNodes)
    }
}