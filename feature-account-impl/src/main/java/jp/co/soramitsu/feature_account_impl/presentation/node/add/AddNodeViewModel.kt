package jp.co.soramitsu.feature_account_impl.presentation.node.add

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.domain.NodeHostValidator
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.node.NodeDetailsRootViewModel

class AddNodeViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val nodeHostValidator: NodeHostValidator,
    private val resourceManager: ResourceManager
) : NodeDetailsRootViewModel(resourceManager) {

    val nodeNameInputLiveData = MutableLiveData<String>()
    val nodeHostInputLiveData = MutableLiveData<String>()

    private val addingInProgressLiveData = MutableLiveData(false)

    val addButtonState = combine(
        nodeNameInputLiveData,
        nodeHostInputLiveData,
        addingInProgressLiveData
    ) { (name: String, host: String, addingInProgress: Boolean) ->
        when {
            addingInProgress -> ButtonState.PROGRESS
            name.isNotEmpty() && nodeHostValidator.hostIsValid(host) -> ButtonState.NORMAL
            else -> ButtonState.DISABLED
        }
    }

    fun backClicked() {
        router.back()
    }

    fun addNodeClicked() {
        val nodeName = nodeNameInputLiveData.value ?: return
        val nodeHost = nodeHostInputLiveData.value ?: return

        addingInProgressLiveData.value = true

        disposables += interactor.addNode(nodeName, nodeHost)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally { addingInProgressLiveData.value = false }
            .subscribe({
                router.back()
            }, ::handleNodeException)
    }
}