package jp.co.soramitsu.feature_account_impl.presentation.node.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.setValueIfNew
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.node.NodeDetailsRootViewModel

class NodeDetailsViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val nodeId: Int,
    private val isSelected: Boolean,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager
) : NodeDetailsRootViewModel(resourceManager) {

    val nodeLiveData = getNode(nodeId).asLiveData()

    val nameEditEnabled = nodeLiveData.map(::mapNodeNameEditState)
    val hostEditEnabled = nodeLiveData.map(::mapNodeHostEditState)

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

    private fun mapNodeNameEditState(node: Node): Boolean {
        return !node.isDefault
    }

    private fun mapNodeHostEditState(node: Node): Boolean {
        return !node.isDefault && !isSelected
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
            }, ::handleNodeException)
    }
}