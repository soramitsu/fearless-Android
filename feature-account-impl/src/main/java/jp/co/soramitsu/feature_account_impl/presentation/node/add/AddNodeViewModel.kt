package jp.co.soramitsu.feature_account_impl.presentation.node.add

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.domain.NodeHostValidator
import jp.co.soramitsu.feature_account_impl.domain.errors.NodeAlreadyExistsException
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

class AddNodeViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val nodeHostValidator: NodeHostValidator,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val nodeNameInputLiveData = MutableLiveData<String>()
    private val nodeHostInputLiveData = MutableLiveData<String>()

    val addButtonEnabled = nodeNameInputLiveData
        .combine(nodeHostInputLiveData) { name, host ->
            mapNodeInputsToAddButtonState(name, host)
        }

    fun backClicked() {
        router.back()
    }

    fun nodeNameChanged(nodeName: String) {
        nodeNameInputLiveData.value = nodeName
    }

    fun nodeHostChanged(nodeHost: String) {
        nodeHostInputLiveData.value = nodeHost
    }

    private fun mapNodeInputsToAddButtonState(name: String, host: String): Boolean {
        return name.isNotEmpty() && nodeHostValidator.hostIsValid(host)
    }

    fun addNodeClicked() {
        nodeNameInputLiveData.value?.let { nodeName ->
            nodeHostInputLiveData.value?.let { nodeHost ->
                disposables += interactor.addNode(nodeName, nodeHost)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        router.back()
                    }, {
                        when (it) {
                            is NodeAlreadyExistsException -> showError(resourceManager.getString(R.string.connection_add_already_exists_error))
                            else -> it.message?.let { showError(it) }
                        }
                    })
            }
        }
    }
}